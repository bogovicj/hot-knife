package net.imglib2.bspline;

import java.util.Arrays;
import java.util.function.Consumer;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;

/**
 * 
 * Compute Bspline coefficients from an image.
 * 
 * @author John Bogovic
 *
 * @param <T> image type 
 * @param <S> coefficient type
 */
public class BSplineDecomposition<T extends RealType<T>, S extends RealType<S>> implements Consumer< RandomAccessibleInterval<S>>
{
	protected final int order;

	protected final int numberOfPoles;

	protected final double[] poles;

	// precompute z_i / ( z_i * z_i - 1 )
	protected final double[] Ci;

	protected final RandomAccessible< T > img;

	protected double tolerance = 1e-6;

	protected int paddingWidth = 4;

	protected int initHorizon = 6;
	
	protected double[] padding;

	protected RandomAccessibleInterval< S > tmpCoefStorage;

	protected Interval originalInterval;

	public BSplineDecomposition( final int order, final RandomAccessible<T> img )
	{
		assert( order <= 5 );

		this.order = order;
		this.img = img;

		this.poles = poles( order );
		this.numberOfPoles = poles.length;
		Ci = polesCi( poles );
		
		padding = new double[ paddingWidth ];
	}

	/**
	 * This constructor will build a cubic bspline decomposition.
	 */
	public BSplineDecomposition( final RandomAccessible<T> img )
	{
		this( 3, img );
	}

	public static double[] polesCi( double[] poles )
	{
		// TODO doc, source, and cite this 
		double[] Ci = new double[ poles.length ];
		for( int i = 0; i < poles.length; i++ )
			Ci[ i ]= poles[ i ] / ( poles[ i ] * poles[ i ] - 1.0 );

		return Ci;
	}

	@Override
	public void accept( final RandomAccessibleInterval< S > coefficients )
	{
		/*
		 * Uncomment the below to re-use a single temporary image.
		 * This breaks Lazy.process
		 */
//		FinalInterval itvl = Intervals.expand( coefficients, 6 );
//		if( tmpCoefStorage == null )
//		{
//			tmpCoefStorage = Util.getSuitableImgFactory( itvl, Util.getTypeFromInterval( coefficients )).create( itvl );
//		}
//		IntervalView<S> paddedImg = Views.translate( tmpCoefStorage, Intervals.minAsLongArray(itvl));


		originalInterval = coefficients;

		FinalInterval itvl = Intervals.expand( coefficients, paddingWidth );
		Img<S> paddedImgZero = Util.getSuitableImgFactory( itvl, Util.getTypeFromInterval( coefficients )).create( itvl );
		IntervalView<S> paddedImg = Views.translate( paddedImgZero, Intervals.minAsLongArray(itvl));

//		System.out.println("orig itvl: " + Util.printInterval( coefficients ));
//		System.out.println("padded itvl: " + Util.printInterval( paddedImg ));

		acceptUnpadded( paddedImg );

		IntervalView<S> subImg = Views.interval( paddedImg, coefficients );
		LoopBuilder.setImages( subImg, coefficients ).forEachPixel( (x,y) -> y.set(x) );
	}

	// for debug purposes, keep track of how many times accept method is called
	private long acceptCount = 0;

	/**
	 * Compute coefficients.
	 * 
	 * @param coefficients the interval and destination in which to store coefficients.
	 */
	@SuppressWarnings("unchecked")
	public void acceptUnpadded( final RandomAccessibleInterval< S > coefficients )
	{
		acceptCount++;

//		System.out.println(" computing coefficients " + acceptCount);
//		System.out.println(" input itvl: " + Util.printInterval(coefficients));
//		long startTime = System.currentTimeMillis();

		int nd = img.numDimensions();
	
		RandomAccess<T> imgAccess = img.randomAccess();
		RandomAccess<S> coefAccess = coefficients.randomAccess();
		RandomAccess<S> coefExtAccess = Views.extendMirrorSingle( coefficients ).randomAccess();

		Point startPoint = new Point( Intervals.minAsLongArray( coefficients ));
		imgAccess.setPosition( startPoint );
		coefAccess.setPosition( startPoint );
		coefExtAccess.setPosition( startPoint );

		S var = coefAccess.get().createVariable();

		for( int d = 0; d < nd; d++ )
		{
			@SuppressWarnings("rawtypes")
			RandomAccess dataAccess;
			if( d == 0 )
				dataAccess = imgAccess;
			else
			{
				dataAccess = coefExtAccess;
			}

			/**
			 * A "small" optimization:
			 * 	Don't need to do recursion over padded areas for last dimensions
			 *  since only the central, un-padded area will be copied into destination interval. 
			 */

			int start;
			Interval itvl;
			IntervalIterator it = getIterator( coefficients, d );
			if( d == (nd - 1) && originalInterval != null )
			{
//				System.out.println("here");
				itvl = originalInterval;
				it = getIterator( coefficients, originalInterval, d );
			}
			else
			{
				itvl = coefficients;
				it = getIterator( coefficients, d );
			}

			while( it.hasNext() )
			{
				it.fwd();
				coefAccess.setPosition( it );
				dataAccess.setPosition( it );
	
				recursion1d( dataAccess, coefAccess, var,
							itvl.dimension( d ), d );
			}
		}

//		long endTime = System.currentTimeMillis();
//		System.out.println( "took " + (endTime - startTime) +" ms" );
	}

	public static IntervalIterator getIterator( 
			final Interval paddedInterval,
			final Interval originalInterval,
			int dim )
	{
		int nd = paddedInterval.numDimensions();

		long[] min = new long[ nd ];
		long[] max = new long[ nd ];
		for ( int d = 0; d < nd; d++ )
		{
			if( d == dim )
			{
				// min and max  the same here
				min[ d ] = originalInterval.min( d );
				max[ d ] = originalInterval.min( d );
			}
			else
			{
				min[ d ] = paddedInterval.min( d );
				max[ d ] = paddedInterval.max( d );
			}
		}
		return new IntervalIterator( min, max );
	}

	public static IntervalIterator getIterator( Interval interval, int dim )
	{
		int nd = interval.numDimensions();

		long[] min = new long[ nd ];
		long[] max = new long[ nd ];
		for ( int d = 0; d < nd; d++ )
		{
			if( d == dim )
			{
				// min and max  the same here
				min[ d ] = interval.min( d );
				max[ d ] = interval.min( d );
			}
			else
			{
				min[ d ] = interval.min( d );
				max[ d ] = interval.max( d );
			}
		}
		return new IntervalIterator( min, max );
	}

	/**
	 * Compute a 1d forward-backward recursion to compute bspline coefficients.
	 * Stores results in the destAccess, reads data from srcAccess.
	 * 
	 * srcAccess and destAccess can look into the same RandomAccessible, 
	 * but need to be different (RandomAccess)es
	 * 
	 * It is the caller's responsibility to ensure the 
	 * access are positioned correctly before calling.
	 * 
	 * @param srcAccess access for the data
	 * @param destAccess access to write results into 
	 * @param previous a temporary variable
	 * @param N width of the recursion
	 * @param dimension the dimension along which to operate
	 * @param tolerance tolerance for initializing coefficients
	 * @param numberOfPoles number of poles
	 * @param poles the poles
	 * @param Ci Ci
	 */
	public static <S extends RealType<S>, T extends RealType<T>> void recursion1dUnpadded(
			final RandomAccess<T> srcAccess,
			final RandomAccess<S> destAccess,
			final S previous,
			final long N, 
			final int dimension,
			final double tolerance,
			final int numberOfPoles,
			final double[] poles,
			final double[] Ci )
	{
		for( int pole_idx = 0; pole_idx < numberOfPoles; pole_idx++ )
		{
			double z = poles[ pole_idx ];

			// causal recursion over coefficients
			// initialize
			double c0 = initializeCausalCoefficients( z, tolerance, dimension, srcAccess );
			destAccess.get().setReal( c0 );
			previous.set( destAccess.get() );

			// recurse fwd
			for( int i = 1; i < N; i++ )
			{
				srcAccess.fwd( dimension );
				destAccess.fwd( dimension );
				
				// c[i] = v[i] + z * c[i-1]
				S coef = destAccess.get();
				coef.setReal( srcAccess.get().getRealDouble() );
				previous.mul( z );
				coef.add( previous );
				
				previous.set( coef );
			}
			// here, destAccess at position N-1

			// anti-causal recursion over coefficients
			// initialize
			initializeAntiCausalCoefficients( z, Ci[pole_idx], previous, destAccess );
			/*
			 * After calling this method:
			 *   destAccess at position N-2
			 *   previous holds the value of coef at N-1
			 */

			for( long i = N-2; i >= 0; i-- )
			{
				// coefs[ i ] = Z1 * ( coefs[i+1] - coefs[ i ]);
				// 		      = -Z1 * ( coefs[i] - coefs[ i + 1 ]);
				S coef = destAccess.get();
				coef.sub( previous );
				coef.mul( -z );

				previous.set( coef );

				srcAccess.bck( dimension );
				destAccess.bck( dimension );
			}
		}
	}
	
	int debugPos = 220;

	/**
	 * Compute a 1d forward-backward recursion to compute bspline coefficients.
	 * Stores results in the destAccess, reads data from srcAccess.
	 * 
	 * srcAccess and destAccess can look into the same RandomAccessible, 
	 * but need to be different (RandomAccess)es
	 * 
	 * It is the caller's responsibility to ensure the 
	 * access are positioned correctly before calling.
	 * 
	 * @param srcAccess access for the data
	 * @param destAccess access to write results into 
	 * @param previous a temporary variable
	 * @param N width of the recursion
	 * @param dimension the dimension along which to operate
	 */
	public <S extends RealType<S>, T extends RealType<T>> void recursion1d(
			final RandomAccess<T> srcAccess,
			final RandomAccess<S> destAccess,
			final S previous,
			final long N, 
			final int dimension )
	{
		boolean debug = false;
		for( int pole_idx = 0; pole_idx < numberOfPoles; pole_idx++ )
		{
			double z = poles[ pole_idx ];

			// causal recursion over coefficients
			// initialize
			double c0 = initializeCausalCoefficients( z, tolerance, dimension, srcAccess );
			destAccess.get().setReal( c0 );
			previous.set( destAccess.get() );

			// recurse fwd
			for( int i = 1; i < N; i++ )
			{
				srcAccess.fwd( dimension );
				destAccess.fwd( dimension );
				
				// c[i] = v[i] + z * c[i-1]
				S coef = destAccess.get();
				coef.setReal( srcAccess.get().getRealDouble() );
				previous.mul( z );
				coef.add( previous );
				
				previous.set( coef );
				
//				if( destAccess.getIntPosition(0) == debugPos || debug )
//				{
//					System.out.println( "fwd coef at " + destAccess.getIntPosition(0) + " : " + coef );
//					System.out.println( "fwd img value at " + srcAccess.getIntPosition(0) + " : " + srcAccess.get() );
//					debug = true;
//				}
			}
			// here, destAccess at position N-1
			
//			System.out.println( " after fwd, prev val : " + previous );
//			System.out.println( " dest pos after fwd : " + Util.printCoordinates( destAccess ));
			
//			// go a little further
//			// N 
//			srcAccess.fwd( dimension );
//			double plus1 = previous.getRealDouble() + z * srcAccess.get().getRealDouble();
//
//			// N+1
//			srcAccess.fwd( dimension );
//			double plus2 = plus1 + z * srcAccess.get().getRealDouble();
//
//			// N+2
//			srcAccess.fwd( dimension );
//			double plus3 = plus2 + z * srcAccess.get().getRealDouble();
//	
//			// initialize reverse
//			// replaces call to initializeAntiCausalCoefficients
//			plus3 += z * plus2;
//			plus3 *= Ci[pole_idx];
//
//	
//			// go backwards
//			plus2 -= plus3;
//			plus2 *= -z;
//
//			plus1 -= plus2;
//			plus1 *= -z;
//	
//			previous.setReal( plus1 );
			
			previous.setReal( padOperation( srcAccess, dimension, previous.getRealDouble(), z, Ci[ pole_idx ] ));

//			System.out.println( " value after pad " + previous );
//			System.out.println( " dest pos after pad: " + Util.printCoordinates( destAccess ));

			/*
			 * After calling this method:
			 *   destAccess at position N-2
			 *   previous holds the value of coef at N-1
			 */
			debugPos = 227;
			for( long i = N-1; i >= 0; i-- )
			{
				// coefs[ i ] = Z1 * ( coefs[i+1] - coefs[ i ]);
				// 		      = -Z1 * ( coefs[i] - coefs[ i + 1 ]);
				S coef = destAccess.get();
				coef.sub( previous );
				coef.mul( -z );

//				if( destAccess.getIntPosition(0) == debugPos || debug )
//				{
//					System.out.println( "rev coef at " + destAccess.getIntPosition(0) + " : " + coef );
//					debug = true;
//				}

				previous.set( coef );

				srcAccess.bck( dimension );
				destAccess.bck( dimension );

			}
		}
	}

	private < R extends RealType<R>> double padOperation( RandomAccess< R > access, int dimension, double last, double z, double Ci )
	{
//		boolean debug = true;

		// store initial position
//		long accessPos = access.getLongPosition( dimension );

//		System.out.println("pad init: " + last);

		access.fwd( dimension );	
//		System.out.println("pad access: " + access.get());
		padding[ 0 ] = access.get().getRealDouble() + z * last;

//		if( access.getIntPosition(0) == debugPos || debug )
//		{
//			System.out.println( "pad fwd coef at " + access.getIntPosition(0) + " : " + padding[ 0 ] );
//		}

		// do paddingWidth more causal operations
		for( int i = 1; i < paddingWidth; i++ )
		{
			access.fwd( dimension );	
			padding[ i ] = access.get().getRealDouble() + z * padding[ i - 1 ];

//			if( access.getIntPosition(0) == debugPos || debug )
//			{
//				System.out.println( "pad fwd coef at " + access.getIntPosition(0) + " : " + padding[ i ] );
//			}
		}
		
		// initialize anticausal
		// coefs[ N-1 ] = Ci * ( coefs[ N-1 ] + z * coefs[ N-2 ] );


		/*
		 * IS IT THE CASE THAT THIS INITIALIZATION IMPOSES A ZERO FIRST DERIVATIVE AT THE BOUNDARY?
		 * IF SO, THIS IS THE CAUSE OF THE ISSUE
		 */
		padding [ paddingWidth - 1 ] += z * padding [ paddingWidth - 2 ];
		padding [ paddingWidth - 1 ] *= Ci;

		// debug
		//padding [ paddingWidth - 1 ] = 0.15365877469604136;

//		if( debug )
//		{
//			System.out.println( "pad rev init: " +  padding[ paddingWidth - 1 ] );
//		}
		
		// do paddingWith anticausal operations
		int j = 1;
		for( int i = paddingWidth - 2; i >=0; i-- )
		{
			padding[ i ] = z * ( padding[ i + 1 ] - padding[ i ] );
//			if( debug )
//			{
//				System.out.println( "pad rev coef at " + (access.getIntPosition(0) - j ) + " : "+ padding[ i ] );
//				j++;
//				debug = true;
//			}
		}

		// reset position
//		access.setPosition( accessPos, dimension );

		return padding[ 0 ];
	}

	/*
	 * The positions of the data and coefs RandomAccess must be set correctly
	 * before calling this.
	 * 
	 * This method has side effects:
	 *   calls coefs.bck( 0 )
	 *   changes the value of previous
	 */
	protected static <T extends RealType<T>> void initializeAntiCausalCoefficients(
			final double z,
			final double c,
			final T previous, // temporary variable
			final RandomAccess<T> coefs )
	{
		T last = coefs.get(); // last has the value at coefs[ N-1 ]

		coefs.bck( 0 );
		previous.set( coefs.get() ); // previous has the value at coefs[ N-2 ]

		// coefs[ N-1 ] = Ci * ( coefs[ N-1 ] + z * coefs[ N-2 ] );
		previous.mul( z );
		last.add( previous );
		last.mul( c );

		previous.set( last );
	}
	
	/**
	 * Data must be 1d or permuted such that the first dimension
	 * is the dimension to be iterated over.
	 * 
	 * See Box 2 of Unser 1999
	 */
	protected static <T extends RealType<T>> double initializeCausalCoefficients(
			final double z,
			final double tolerance,
			final int dimension,
			final RandomAccess<T> dataAccess )
	{
		// TODO this method is wasteful if the interval is zero-extended.
		// Need this when working block-wise though
		
		int horizon = 6;
//		if( tolerance > 0.0 )
//		{
//			horizon = (int)(Math.ceil( Math.log( tolerance )  / Math.log( Math.abs( z ))));
//		}
//		else
//			horizon = 6;
		
		/*
		 * Note:
		 * ./Core/ImageFunction/include/itkBSplineDecompositionImageFilter.hxx
		 * may look like it starts starts zn equal to z, but
		 * it initializes sum equal to the first value of the data.
		 * 
		 * Below is the "accelerated loop" in the code above.
		 * 
		 * Box 2 of Unser 1999 suggests it should start at 1.0
		 * 
		 */
		double zn = z;
		double sum = dataAccess.get().getRealDouble();
		for( int i = 0; i < horizon; i++ )
		{
			dataAccess.bck( dimension );
			sum += zn * dataAccess.get().getRealDouble();

			zn *= z;
		}
		
		// back to starting location
		dataAccess.move( horizon, dimension );
	
		return sum;
	}

	public String toString()
	{
		StringBuffer s = new StringBuffer();
		s.append("BSplineDecomposition\n");
		s.append("  Spline order: " + order + "\n");
		s.append("  Spline poles: " + Arrays.toString(poles) + "\n");
		s.append("  Num poles   : " + numberOfPoles + "\n");

		return s.toString();
	}

	/**
	 * Returns the poles for the filter for a spline of a given order. 
	 * See Unser, 1997. Part II, Table I for Pole values.
	 * 
	 * @param splineOrder the order of the bspline
	 * @return an array of the poles
	 */
	public static double[] poles( final int splineOrder )
	{
		switch( splineOrder )
		{
		case 0:
			return new double[ 0 ]; // empty
		case 1:
			return new double[ 0 ]; // empty
		case 2:
			return new double[]{ Math.sqrt( 8.0 ) - 3.0 };
		case 3:
			return new double[]{ Math.sqrt( 3.0 ) - 2.0 };
		case 4:
			return new double[]{ 
				Math.sqrt( 664.0 - Math.sqrt(438976.0) ) + Math.sqrt(304.0) - 19.0,
				Math.sqrt( 664.0 + Math.sqrt(438976.0) ) - Math.sqrt(304.0) - 19.0
			};
		case 5:
			return new double[]{ 
					Math.sqrt( 135.0 / 2.0 - Math.sqrt(17745.0 / 4.0) ) + Math.sqrt(105.0 / 4.0) - 13.0 / 2.0,
					Math.sqrt( 135.0 / 2.0 + Math.sqrt(17745.0 / 4.0) ) - Math.sqrt(105.0 / 4.0) - 13.0 / 2.0
			};
		default:
			return null;
		}
	}
}