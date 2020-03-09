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
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;

/**
 * 
 * Bspline coefficient.
 * 
 * @author John Bogovic
 *
 * @param <T> image type 
 * @param <S> coefficient type
 */
public class BSplineDecomposition<T extends RealType<T>, S extends RealType<S>> implements Consumer< RandomAccessibleInterval<S>>
{
	protected final int order;
	
	// TODO make protected
	public final int numberOfPoles;
	public final double[] poles;
	protected final double[] Ci;

	protected final RandomAccessible<T> img;
	
	protected double tolerance = 1e-6;

	public BSplineDecomposition( final int order, final RandomAccessible<T> img )
	{
		assert( order <= 5 );

		this.order = order;
		this.img = img;

		this.poles = poles( order );
		this.numberOfPoles = poles.length;
		Ci = polesCi( poles );
	}

	/**
	 * This constructor will build a cubic bspline decomposition.
	 */
	@SuppressWarnings("unchecked")
	public BSplineDecomposition( final RandomAccessible<T> img )
	{
		this( 3, img );
	}

	public static double[] polesCi( double[] poles )
	{
		double[] Ci = new double[ poles.length ];
		for( int i = 0; i < poles.length; i++ )
			Ci[ i ]= poles[ 0 ] / ( poles[0] * poles[0] - 1.0 );
	
		return Ci;
	}

//			final RandomAccess<T> srcAccess,
//			final RandomAccess<S> destAccess,
//			final long N, 
//			final int dimension,
//			final double tolerance,
//			final int numberOfPoles,
//			final double[] poles,
//			final double[] Ci )
	
	public static void setAccessPosition( final Localizable loc, RandomAccess<?> ra )
	{
	}

	private long acceptCount = 0;
	@SuppressWarnings("unchecked")
	@Override
	public void accept( final RandomAccessibleInterval< S > coefficients )
	{

		acceptCount++;
		System.out.println(" computing coefficients " + acceptCount);

		long startTime = System.currentTimeMillis();
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
				dataAccess = coefExtAccess;

			IntervalIterator it = getIterator( coefficients, d );
			while( it.hasNext() )
			{
				it.fwd();
				coefAccess.setPosition( it );
				dataAccess.setPosition( it );

				recursion1d( dataAccess, coefAccess, var,
							coefficients.dimension( d ), d,
							tolerance, numberOfPoles, poles, Ci );
				
//				// TODO move this check out of the inner loop
//				if( d == 0 )
//				{
//					imgAccess.setPosition( it );
//					coefAccess.setPosition( it );
//					//System.out.println( "pos: " + Util.printCoordinates( it ));
//
//					recursion1d( imgAccess, coefAccess, var,
//							coefficients.dimension( d ), d,
//							tolerance, numberOfPoles, poles, Ci );
//				}
//				else
//				{
//					coefAccess.setPosition( it );
//					coefExtAccess.setPosition( it );
//
//					recursion1d( coefExtAccess, coefAccess, var,
//							coefficients.dimension( d ), d,
//							tolerance, numberOfPoles, poles, Ci );
//				}
				
				
			}
		}
		long endTime = System.currentTimeMillis();
		System.out.println( "took " + (endTime - startTime) +" ms" );
	}

	public static <S extends RealType<S>> RandomAccessible<S> get1d(
			final RandomAccessible<S> whole,
			final int dim,
			final Localizable position )
	{
		MixedTransform t = to1dRow( whole.numDimensions(), dim, position );
		MixedTransformView<S> subset = new MixedTransformView<>( whole, t );
		return subset;
	}
	
	public static <S extends RealType<S>> RandomAccessibleInterval<S> get1dSubset(
			final RandomAccessibleInterval<S> whole,
			final int dim,
			final Localizable position )
	{
		MixedTransform t = to1dRow( whole.numDimensions(), dim, position );
		MixedTransformView<S> subset = new MixedTransformView<>( whole, t );
		return Views.interval( subset, new FinalInterval( whole.dimension( dim ) ));
	}

	/**
	 * Returns the transformation that is used  above
	 * <p>
	 * Warning: The transformations used in {@link Views} are always
	 * inverse to the operations that are performed by the views.
	 * <p>
	 *
	 * @param numDimensions Number of dimensions including that dimension
	 *                      that is sliced / inserted.
	 * @param d             Index of that dimension that is kept
	 * @param pos           Position of the slice / value of the coordinate that's
	 *                      inserted.
	 * @return Transformation that inserts a coordinate at the given index.
	 */
	public static MixedTransform to1dRow( final int numDimensions, final int d, final Localizable pos )
	{
		final MixedTransform t = new MixedTransform( 1, numDimensions );
		final long[] translation = new long[ numDimensions ];
		final boolean[] zero = new boolean[ numDimensions ];
		final int[] component = new int[ numDimensions ];

		for ( int i = 0; i < numDimensions; ++i )
		{
			if( i == d )
			{
				zero[ i ] = false;
				component[ i ] = 0;
				translation[ i ] = 0;
			}
			else
			{
				translation[ i ] = pos.getIntPosition( i );
				zero[ i ] = true;
			}
		}

		t.setTranslation( translation );
		t.setComponentZero( zero );
		t.setComponentMapping( component );
		return t;
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
	public static <S extends RealType<S>, T extends RealType<T>> void recursion1d(
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
//			System.out.println("pole_idx: " + pole_idx );

			double z = poles[ pole_idx ];

			// causal recursion over coefficients
			// initialize
			double c0 = initializeCausalCoefficients( z, tolerance, dimension, srcAccess );
			destAccess.get().setReal( c0 );
			previous.set( destAccess.get() );

//			System.out.println("Coefs after init");
//			printCoefs( coefficients );

//			System.out.println("FWD");
			// recurse
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

//			System.out.println("Coefs after fwd");
//			printCoefs( coefficients );
			
			// coefAccess at position N-1

			// anti-causal recursion over coefficients
			// initialize
			initializeAntiCausalCoefficients( z, Ci[pole_idx], previous, destAccess );
			/*
			 * After calling this method:
			 *   coefAccess at position N-2
			 *   previous holds the value of coef at N-1
			 */


//			System.out.println("Coefs after rev init");
//			printCoefs( coefficients );

//			System.out.println("REV");
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

//			System.out.println("Coefs after rev");
//			printCoefs( coefficients );

		}
	}

	/*
	 * The positions of the data and coefs RandomAccess must be set correctly
	 * before calling this.
	 * 
	 * This method has side effects:
	 *   calls coefs.bck( 0 )
	 *   changes the value of previous
	 */
	public static <T extends RealType<T>> void initializeAntiCausalCoefficients(
			final double z,
			final double c,
			final T previous, // temporary variable
			final RandomAccess<T> coefs )
	{
		// TODO make this method protected
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
	public static <T extends RealType<T>> double initializeCausalCoefficients(
			final double z,
			final double tolerance,
			final int dimension,
			final RandomAccess<T> dataAccess )
	{
		// TODO make this method protected / private

		// TODO this method is wasteful if the interval is zero-extended.
		// Need this when working block-wise though
		
		int horizon;
		if( tolerance > 0.0 )
		{
			horizon = (int)(Math.ceil( Math.log( tolerance )  / Math.log( Math.abs( z ))));
		}
		else
			horizon = 6;
		
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
			return new double[ 0 ];
		case 1:
			return new double[ 0 ];
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

	public <S extends RealType<S>> void printCoefs( final RandomAccessibleInterval<S> coefs )
	{
		System.out.println( "coefs: ");
		Cursor<S> c = Views.iterable( coefs ).cursor();
		while( c.hasNext() )
		{
			S v = c.next();
			String prefix = "  ";
			System.out.print( prefix + v );
		}
		System.out.print( "\n\n");
	}

	public <S extends RealType<S>> void printCoefs2d( final RandomAccessibleInterval<S> coefs )
	{
		System.out.println( "\ncoefs: ");
		Cursor<S> c = Views.iterable( coefs ).cursor();
		int yp = -1;
		while( c.hasNext() )
		{
			S v = c.next();
			String prefix = "  ";
			if( yp != -1 && c.getIntPosition( 1 ) != yp )
				prefix = "\n  ";

			yp = c.getIntPosition( 1 );
			System.out.print( prefix + v );

		}
		System.out.print( "\n");
	}
}
