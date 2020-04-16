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
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.neighborhood.Neighborhood;
import net.imglib2.neighborhood.RectangleShape;
import net.imglib2.position.transform.Floor;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;


/**
 * Performs cubic b-spline interpolation
 *
 * @author John Bogovic
 * @author Stephan Saalfeld
 */
public class BSplineCoefficientsInterpolator extends Floor< RandomAccess< Neighborhood< DoubleType >>> implements RealRandomAccess< DoubleType >, InterpolatorFactory< DoubleType, RandomAccessibleInterval<DoubleType> >, Localizable
{
	public static final double SQRT3 = Math.sqrt ( 3.0 );
	
	// from Unser box 2 page 26
	public static final double Z1 = SQRT3 - 2;

	public static final double Ci = -Z1 / ( 1 - (Z1*Z1));
	
	public static final double ONESIXTH = 1.0 / 6.0;
	public static final double TWOTHIRDS = 2.0 / 3.0;
	public static final double FOURTHIRDS = 4.0 / 3.0;

	final protected DoubleType value;
	
	final protected double[][] weights;
	
	final protected int bsplineOrder;

	final protected int kernelWidth;
	
	protected final RectangleShape shape;
	
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
	public static RectangleShape shapeFromOrder( int bsplineOrder )
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
				return new RectangleShape( 2, false ); // need four samples - round up
			case 4:
				return new RectangleShape( 2, false ); // need five samples - round up
			case 5:
				return new RectangleShape( 3, false ); // need six samples - round up
			default:
				return null;
		}
	}

	public BSplineCoefficientsInterpolator( final BSplineCoefficientsInterpolator interpolator, final int order )
	{
		super( interpolator.target.copyRandomAccess() );

		this.bsplineOrder = interpolator.bsplineOrder;
		this.shape = shapeFromOrder( bsplineOrder );

		value = new DoubleType();

		// this should change when we stop using rectangleshapes
		kernelWidth = 2 * shape.getSpan() + 1;

		weights = new double[ numDimensions() ][ kernelWidth ];
	}

	public BSplineCoefficientsInterpolator( final RandomAccessible< DoubleType > coefficients, final int order )
	{
		this( coefficients, order, shapeFromOrder( order ));
	}

	private BSplineCoefficientsInterpolator( final RandomAccessible< DoubleType > coefficients, final int order, final RectangleShape shape )
	{
		super( shape.neighborhoodsRandomAccessible( coefficients ).randomAccess() );

		this.shape = shape;
		this.bsplineOrder = order;

		value = new DoubleType();

		// this should change when we stop using rectangleshapes
		kernelWidth = 2 * shape.getSpan() + 1;

		weights = new double[ numDimensions() ][ kernelWidth ];
	}

	@Override
	public DoubleType get()
	{
		fillWeights();

		double accumulator = 0;
		final Cursor< DoubleType > c = target.get().cursor();
		while ( c.hasNext() )
		{
			double tmp = c.next().getRealDouble();
			for ( int d = 0; d < numDimensions(); d++ )
			{
				final int index = ( int ) ( c.getLongPosition( d ) - target.getLongPosition( d ) + shape.getSpan() );

//				// debug
//				if( index >=  weights[d].length )
//				{
//					System.out.println( "cpos: " + c.getLongPosition( d ) );
//					System.out.println( "tpos: " + target.getLongPosition( d ) );
//					System.out.println( "span: " + shape.getSpan());
//				}

//				tmp *= weights[ d ][ index ];

				// This check seems necessary after using too-big rectangle shape
				if( index <  weights[d].length )
					tmp *= weights[ d ][ index ];
			}
			accumulator += tmp;
		}

		value.setReal( accumulator );
		return value;
	}
	
	@Override
	public long getLongPosition(int d)
	{
		return (long)Math.floor( position[ d ]);
	}

	// TODO generalize for any order spline
	protected void fillWeights()
	{
		final Neighborhood< DoubleType > rect = target.get();
		for ( int d = 0; d < numDimensions(); d++ )
		{
			final double pos = position[ d ];
			final long min = rect.min( d );
			final long max = rect.max( d );
			for ( long i = min; i <= max; ++i )
				weights[ d ][ ( int ) ( i - min ) ] = evaluate3( pos - i );
		}
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
	
	/*
	 * Third order spline kernel
	 */
	public static double evaluate3( final double u )
	{
		final double absValue = Math.abs( u );
		final double sqrValue = u * u;
		if ( absValue < 1.0 )
			return ( 4.0 - 6.0 * sqrValue + 3.0 * sqrValue * absValue );
		else if ( absValue < 2.0 )
			return ( 8.0 - 12.0 * absValue + 6.0 * sqrValue - sqrValue * absValue );
		else
			return 0.0;
	}

	/*
	 * Third order spline kernel
	 */
	public static double evaluate3Normalized( final double u )
	{
		final double absValue = Math.abs( u );
		final double sqrValue = u * u;
		if ( absValue <= 1.0 )
			return ( TWOTHIRDS - sqrValue + 0.5 * sqrValue * absValue );
		else if ( absValue < 2.0 )
		{
			final double twoMinusAbsValue = 2 - absValue;
			return twoMinusAbsValue * twoMinusAbsValue * twoMinusAbsValue * ONESIXTH;
		}
		else
			return 0.0;
	}

	@Override
	public BSplineCoefficientsInterpolator copy()
	{
		return new BSplineCoefficientsInterpolator( this, this.bsplineOrder );
	}

	@Override
	public RealRandomAccess<DoubleType> create(RandomAccessibleInterval<DoubleType> f)
	{
		return copy();
	}

	@Override
	public RealRandomAccess<DoubleType> create(RandomAccessibleInterval<DoubleType> f, RealInterval interval)
	{
		return copy();
	}

	@Override
	public RealRandomAccess<DoubleType> copyRealRandomAccess()
	{
		return copy();
	}

}
