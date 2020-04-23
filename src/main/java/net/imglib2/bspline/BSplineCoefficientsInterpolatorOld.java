/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2018 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imglib2.bspline;


import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.neighborhood.GeneralRectangleShape;
import net.imglib2.neighborhood.Neighborhood;
import net.imglib2.neighborhood.RectangleShape;
import net.imglib2.position.transform.Floor;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;


/**
 * Performs cubic b-spline interpolation
 *
 * @author John Bogovic
 * @author Stephan Saalfeld
 */
//public class BSplineCoefficientsInterpolator<T extends RealType<T>> implements RealRandomAccess< T >, InterpolatorFactory< T, RandomAccessibleInterval<T> >, Localizable
public class BSplineCoefficientsInterpolatorOld<T extends RealType<T>> extends Floor< RandomAccess< Neighborhood< T >>> implements RealRandomAccess< T >, InterpolatorFactory< T, RandomAccessibleInterval<T> >, Localizable
{
	final protected T value;
	
	final protected double[][] weights;
	
	final protected int bsplineOrder;

	final protected int kernelWidth;
	
	final AbstractBsplineKernel kernel;

	protected final RectangleShape shape;
	
	protected final boolean oddOrder;

	boolean DEBUG = false;
	

	/**
	 * This is unfortunately necessary because rectangles always have an extents
	 * equal to an odd number of pixels.  Need to round up to the nearest odd number
	 * when an even number of samples are needed.
	 * 
	 * This will be wasteful in general.   We should move away from using RectangleShapes here soon.
	 * 
	 * @return an appropriate rectangle 
	 */
	public static RectangleShape shapeFromOrder( int bsplineOrder, boolean optimized )
	{
		assert( bsplineOrder <= 5  && bsplineOrder >= 0 );

		switch ( bsplineOrder ){
			case 0:
				return new RectangleShape( 0, false ); // need one sample - correct
			case 1:
				return new RectangleShape( 1, false ); // need two samples - round up
			case 2: 
				return new RectangleShape( 1, false ); // need three samples - correct
			case 3:
				if( optimized )
				{
					System.out.println( "general shape ");
					return new GeneralRectangleShape( 4, -1, false ); // need four samples - round up
				}
				else
				{
					System.out.println( "Old shape");
					return new RectangleShape( 2, false ); // need four samples - round up
				}
			case 4:
				return new RectangleShape( 2, false ); // need five samples - round up
			case 5:
				return new RectangleShape( 3, false ); // need six samples - round up
			default:
				return null;
		}
	}

	public static AbstractBsplineKernel makeKernel( int order )
	{
		assert( order <= 5  && order >= 0 );

		switch ( order ){
			case 0:
				return new BsplineKernel0();
			case 1:
				return new BsplineKernel1();
			case 2: 
				return new BsplineKernel2();
			case 3:
				return new BsplineKernel3();
			case 4:
				return new BsplineKernel4();
			case 5:
				return null; // TODO
			default:
				return null;
		}
	}

	public BSplineCoefficientsInterpolatorOld( final BSplineCoefficientsInterpolatorOld< T > interpolator, final int order, final T type )
	{
		this( interpolator, order, type, true );
	}

	public BSplineCoefficientsInterpolatorOld( final BSplineCoefficientsInterpolatorOld< T > interpolator, final int order, final T type, 
			final boolean optimized )
	{
		super( interpolator.target.copyRandomAccess() );
		
		oddOrder = order % 2 == 1;

		this.bsplineOrder = interpolator.bsplineOrder;
		this.shape = shapeFromOrder( bsplineOrder, optimized );

		value = type.copy();

		Interval shapeBBox = shape.getStructuringElementBoundingBox( target.numDimensions() );
		kernelWidth = (int) shapeBBox.dimension( 0 ); // size is identical for all dims
		
		System.out.println( "kernelWidth: " + kernelWidth );

		// this should change when we stop using rectangleshapes
		//kernelWidth = 2 * shape.getSpan() + 1;
//		offset = shape.getSpan();

		kernel = makeKernel( order );

		weights = new double[ numDimensions() ][ kernelWidth ];
	}

	public BSplineCoefficientsInterpolatorOld( final RandomAccessible< T > coefficients, final int order, final T type )
	{
		this( coefficients, order, type, true );
	}

	public BSplineCoefficientsInterpolatorOld( final RandomAccessible< T > coefficients, final int order, final T type, boolean optimized )
	{
		this( coefficients, order, type, shapeFromOrder( order, optimized ), optimized );
	}

	private BSplineCoefficientsInterpolatorOld( final RandomAccessible< T > coefficients, final int order, final T type, 
			final RectangleShape shape, final boolean optimized )
	{
		super( shape.neighborhoodsRandomAccessible( coefficients ).randomAccess() );

		this.shape = shape;
		this.bsplineOrder = order;
		oddOrder = order % 2 == 1;

		value = type.copy();

		Interval shapeBBox = shape.getStructuringElementBoundingBox( target.numDimensions() );
		kernelWidth = (int) shapeBBox.dimension( 0 ); // size is identical for all dims

		System.out.println( "kernelWidth: " + kernelWidth );

		// this should change when we stop using rectangleshapes
//		kernelWidth = 2 * shape.getSpan() + 1;
//		offset = shape.getSpan();

		kernel = makeKernel( order );

		weights = new double[ numDimensions() ][ kernelWidth ];
	}

	@Override
	public T get()
	{
		fillWeights();
//		System.out.println( "get NEW");

		int offset = bsplineOrder / 2;
		double accumulator = 0;
		final Cursor< T > c = target.get().cursor();
		while ( c.hasNext() )
		{
			double tmp = c.next().getRealDouble();
			for ( int d = 0; d < numDimensions(); d++ )
			{

				final int index = ( int ) ( c.getLongPosition( d ) - target.getLongPosition( d ) + shape.getSpan() );

				// This check seems necessary after using too-big rectangle shape
				if( index <  weights[d].length )
					tmp *= weights[ d ][ index ];
				else
					tmp = 0;
			}
			accumulator += tmp;
		}

		value.setReal( accumulator );
		return value;
	}
	
	@Override
	public long getLongPosition(int d)
	{
//		return (long)Math.round( position[ d ]);
		return (long)Math.floor( position[ d ]);
	}

	// TODO generalize for any order spline
	protected void fillWeights()
	{
//		System.out.println("fill weights");
		final Neighborhood< T > rect = target.get();
		for ( int d = 0; d < numDimensions(); d++ )
		{
			final double pos = position[ d ];
			final long min = rect.min( d );
			final long max = rect.max( d );
//			System.out.println("      pos : " +  pos );
//			System.out.println("      min : " +  min );
			for ( long i = min; i <= max; ++i )
			{
				weights[ d ][ ( int ) ( i - min ) ] = kernel.evaluateNorm( pos - i );

//				System.out.println("        i : " +  i );
//				System.out.println("  i - min : " + (i-min));
//				System.out.println("  pos-i : " + (pos-i));
//				System.out.println("      w : " + weights[ d ][ (int)(i-min)] );
//				System.out.println(" ");
			}
		}
//		System.out.println(" ");
//		System.out.println(" ");
	} 

	public <T extends RealType<T>> void printValues( RandomAccessibleInterval<T> vals )
	{
		System.out.println( "\nvalues: ");
		Cursor<T> c = Views.flatIterable( vals ).cursor();
		int yp = -1;
		while( c.hasNext() )
		{
			T v = c.next();
			String prefix = "  ";
			if( yp != -1 && c.getIntPosition( 1 ) != yp )
				prefix = "\n  ";

			yp = c.getIntPosition( 1 );
			System.out.print( prefix + v );

		}
		System.out.print( "\n");
	}

	@Override
	public BSplineCoefficientsInterpolatorOld<T> copy()
	{
		return new BSplineCoefficientsInterpolatorOld<T>( this, this.bsplineOrder, value );
	}

	@Override
	public RealRandomAccess<T> create( RandomAccessibleInterval<T> f)
	{
		return copy();
	}

	@Override
	public RealRandomAccess< T > create(RandomAccessibleInterval< T > f, RealInterval interval)
	{
		return copy();
	}

	@Override
	public RealRandomAccess< T > copyRealRandomAccess()
	{
		return copy();
	}

}
