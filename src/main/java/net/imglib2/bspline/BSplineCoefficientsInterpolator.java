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

import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.position.transform.FloorOffset;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;


/**
 * Performs cubic b-spline interpolation
 *
 * @author John Bogovic
 * @author Stephan Saalfeld
 */
public class BSplineCoefficientsInterpolator extends FloorOffset< RandomAccess< DoubleType > > implements RealRandomAccess< DoubleType >, InterpolatorFactory< DoubleType, RandomAccessibleInterval<DoubleType> >, Localizable
{
	public static final double SQRT3 = Math.sqrt ( 3.0 );
	
	// from Unser box 2 page 26
	public static final double Z1 = SQRT3 - 2;

	public static final double Ci = -Z1 / ( 1 - (Z1*Z1));

	final protected RandomAccessible<DoubleType> coefficients;

	final protected DoubleType accumulator;

	final protected DoubleType tmp;

	final protected DoubleType w;
	
	final protected DoubleType value;
	
	final public double[][] weights; // TODO make protected
	
	final protected int bsplineOrder;

	final protected int kernelWidth;

	final protected long[] coefMin;

	final protected long[] coefMax;

	final protected int[] ZERO;
	
//	final protected int[] coefDimOffsets;
	
	boolean DEBUG = false;
	
	final static private int kernelWidth( final int order )
	{
		return (order + 1);
	}

	final static private long[] createOffset( final int order, final int n )
	{
		final int r = ( kernelWidth( order ) - 1 ) / 2;
		final long[] offset = new long[ n ];
		Arrays.fill( offset, -r );
		return offset;
	}

//	public static int[] createCoefLoopOffsets( int[] weightDim, long[] coefDims )
//	{
//		int nd = coefDims.length;
//		int[] coefDimOffsets = new int[ nd ];
//		coefDimOffsets[ 0 ] = 1;
//
//		// compute the total number of weights and 
//		int Nweights = 1;
//		for ( int d = 1; d < nd; ++d )
//		{
//			Nweights *= weightDim[ d ];
//			coefDimOffsets[ d ] = (int)coefDims[ d ];
//		}
//		
//		int[] offset = new int[ Nweights ];
//		int[] pos = new int[ nd ];
//		for( int i = 0; i < Nweights; i++ )
//		{
//			for ( int d = nd-1; d <= 0; d-- )
//			{
//				if( i % weightDim[ d ] == 0 )
//				{
//					
//				}
//			}
//			
//		}
//
//		return offset;
//	}

	public BSplineCoefficientsInterpolator( final BSplineCoefficientsInterpolator interpolator, final int order )
	{
		super( interpolator.target.copyRandomAccess(), createOffset( order, interpolator.numDimensions() ) );

		this.bsplineOrder = order;
		kernelWidth = kernelWidth( order );

		value = interpolator.target.get().createVariable();
		accumulator = new DoubleType();
		tmp = new DoubleType();
		w = new DoubleType();

		coefficients= interpolator.coefficients;

//		coefDimOffsets = null;
		coefMin = new long[ numDimensions() ];
		coefMax = new long[ numDimensions() ];
		for ( int d = 0; d < n; ++d )
		{
			position[ d ] = interpolator.position[ d ];
			discrete[ d ] = interpolator.discrete[ d ];
		}

		weights = new double[ numDimensions() ][ kernelWidth ];
		ZERO = new int[ numDimensions() ];
	}

	public BSplineCoefficientsInterpolator( final RandomAccessible< DoubleType > coefficients, final int order )
	{
		super( coefficients.randomAccess(), createOffset( order, coefficients.numDimensions() ) );

		this.bsplineOrder = order;
		kernelWidth = kernelWidth( order );

		value = new DoubleType();
		accumulator = new DoubleType();
		tmp = new DoubleType();
		w = new DoubleType();
		
		this.coefficients = coefficients;

		coefMin = new long[ numDimensions() ];
		coefMax = new long[ numDimensions() ];

//		coefDimOffsets = new int[ numDimensions() ];
//		coefDimOffsets[ 0 ] = 1;
//		for ( int d = 1; d < n; ++d )
//		{
//			coefDimOffsets[ d ] = (int)coefInterval.dimension( d - 1 );
//		}

		weights = new double[ numDimensions() ][ kernelWidth ];
		ZERO = new int[ numDimensions() ];
	}

	public RandomAccessible< DoubleType > coefficients()
	{
		return coefficients;
	}
	
	public void printPosition()
	{
		System.out.println( "interp position : " + Arrays.toString( position ));
		System.out.println( "target position : " + Util.printCoordinates( target ));
	}
	
	@Override
	public DoubleType get()
	{
		fillWeights();
		fillWindow();
		accumulator.setZero();
		
		Cursor<DoubleType> c = Views.zeroMin( 
				Views.interval( 
						coefficients,
						coefMin, coefMax )
				).cursor();

		while( c.hasNext() )
		{
			tmp.setReal( c.next().getRealDouble() );
			for( int d = 0; d < numDimensions(); d++ )
			{
//				double ww = weights[ d ][ c.getIntPosition( d ) ];
//				System.out.println( "ci : " + c.getIntPosition( d ) );
//				System.out.println( "w  : " +  ww );

				tmp.mul( weights[ d ][ c.getIntPosition( d ) ]);
			}
			//System.out.println( "tmp: " + tmp );
			accumulator.add( tmp );
		}

		value.setReal( accumulator.getRealDouble() );
		return value;
	}
	
	@Override
	public long getLongPosition(int d)
	{
		return (long)Math.floor( position[ d ]);
	}

	public void fillWindow() // TODO make protected
	{
		for( int d = 0; d < numDimensions(); d++ )
		{
			coefMin[ d ] = (long)Math.floor( position[ d ] ) + offset[ d ]; 
			coefMax[ d ] = coefMin[ d ] + kernelWidth - 1;
		}
	}

	public void fillWeights() // TODO make protected
	{
		double j = 0;
		for( int d = 0; d < numDimensions(); d++ )
		{
			// j is a double that will take integer values
			// starts at the smallest integer value in the support 
			// of the b-spline kernel
			j = Math.floor( position[ d ] ) + offset[ d ]; 
			for( int i = 0; i < kernelWidth; i++ )
			{
//				double dist = position[ d ] - j;
//				System.out.println( "dist: " + dist );
//				System.out.println( "j   : " + j );
				weights[ d ][ i ] = evaluate3( position[ d ] - j );
				j++;
			}
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

	@Override
	public BSplineCoefficientsInterpolator copy() {
		return new BSplineCoefficientsInterpolator( this, this.bsplineOrder );
	}

	@Override
	public RealRandomAccess<DoubleType> create(RandomAccessibleInterval<DoubleType> f) {
		// TODO do something better?
		return copy();
	}

	@Override
	public RealRandomAccess<DoubleType> create(RandomAccessibleInterval<DoubleType> f, RealInterval interval) {
		// TODO do something better?
		return copy();
	}

	@Override
	public RealRandomAccess<DoubleType> copyRealRandomAccess() {
		// TODO do something better?
		return copy();
	}


}
