package org.janelia.saalfeldlab.bspline;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

public class BSplineCoefCorrectnessTest
{

	RealRandomAccessible<DoubleType> const1d;
	RealRandomAccessible<DoubleType> linear1d;
	RealRandomAccessible<DoubleType> quadratic1d;
	RealRandomAccessible<DoubleType> cubic1d;
	RealRandomAccessible<DoubleType> quartic1d;
	Interval coefItvl1d;
	Interval testItvl1d;;

	@Before
	public void setup()
	{
		const1d = ConstantUtils.constantRealRandomAccessible( new DoubleType( 1 ), 1 );
		linear1d = polynomialReal1dC( new double[]{ 1, 2 } );
		quadratic1d = polynomialReal1dZ( new double[]{ 14, 16 } );
		cubic1d = polynomialReal1dZ( new double[]{ 14, 15, 16 } );

		coefItvl1d = new FinalInterval( new long[]{ 32 });
		testItvl1d = new FinalInterval( new long[]{ 12 }, new long[]{ 18 });

	}

	@Test
	public void testSplines1d()
	{
		final double delta = 1e-6;

		// order two bspline
		runTest1d( 2, const1d, "constant", coefItvl1d, testItvl1d, delta );
		runTest1d( 2, linear1d, "linear", coefItvl1d, testItvl1d, delta );
		runTest1d( 2, quadratic1d, "quadratic", coefItvl1d, testItvl1d, delta );

		// order three bspline
		runTest1d( 3, const1d, "constant", coefItvl1d, testItvl1d, delta );
		runTest1d( 3, linear1d, "linear", coefItvl1d, testItvl1d, delta );
		runTest1d( 3, quadratic1d, "quadratic", coefItvl1d, testItvl1d, delta );
		runTest1d( 3, cubic1d, "cubic", coefItvl1d, testItvl1d, delta );

		// order four bspline
		runTest1d( 4, const1d, "constant", coefItvl1d, testItvl1d, delta );
		runTest1d( 4, linear1d, "linear", coefItvl1d, testItvl1d, delta );
		runTest1d( 4, quadratic1d, "quadratic", coefItvl1d, testItvl1d, delta );
		runTest1d( 4, cubic1d, "cubic", coefItvl1d, testItvl1d, delta );

//		// order five bspline
//		runTest1d( 5, const1d, "constant", coefItvl1d, testItvl1d, delta );
//		runTest1d( 5, linear1d, "linear", coefItvl1d, testItvl1d, delta );
//		runTest1d( 5, quadratic1d, "quadratic", coefItvl1d, testItvl1d, delta );
//		runTest1d( 5, cubic1d, "cubic", coefItvl1d, testItvl1d, delta );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T extends RealType<T>> void runTest1d( 
			final int order, 
			final RealRandomAccessible<T> realImg, 
			final String baseMessage,
			final Interval coefInterval, 
			final Interval testInterval, 
			final double delta )
	{
		RandomAccessible<T> img = Views.raster( realImg );
		BSplineCoefficientsInterpolator<DoubleType> est = new BSplineCoefficientsInterpolatorFactory( img, coefInterval, order ).create( img );

		RealRandomAccess<T> trueRa = realImg.realRandomAccess();
		IntervalIterator it = new IntervalIterator( testInterval );
		while( it.hasNext() )
		{
			it.fwd();

			trueRa.setPosition( it.getDoublePosition( 0 ) + 0.5, 0 );
			est.setPosition( it.getDoublePosition( 0 ) + 0.5, 0 );

			Assert.assertEquals(String.format("%s : order %d spline at %f", baseMessage, order, it.getDoublePosition(0)),
					trueRa.get().getRealDouble(), 
					est.get().getRealDouble(),
					delta );
		}
	}

	public static <T extends RealType<T>> RandomAccessible<T> separableImage( final T t, final BiConsumer<Localizable,T>... funs1d )
	{
		BiConsumer<Localizable, T> f = new BiConsumer<Localizable,T>()
		{
			@Override
			public void accept( Localizable l, T t )
			{
				T tmp = t.createVariable();
				Point p = new Point( 1 );
				t.setOne();
				for( int i = 0; i < funs1d.length; i++ )
				{
					p.setPosition( l.getIntPosition( i ), 0 );
					funs1d[ i ].accept( p , tmp );
					t.mul( tmp );
				}
			}
		};

		Supplier<T> s = new Supplier<T>()
		{
			@Override
			public T get() {
				return t.createVariable();
			}
		};

		return new FunctionRandomAccessible<T>( 3, f, s );
	}

	public static RandomAccessible<DoubleType> polynomialImg1dC( final double[] coefs )
	{
		return Views.raster( polynomialReal1dC( coefs ));
	}

	public static RealRandomAccessible<DoubleType> polynomialReal1dC( final double[] coefs )
	{
		return new FunctionRealRandomAccessible<>( 1, polynomial1dC( coefs ), DoubleType::new );
	}

	public static RealRandomAccessible<DoubleType> polynomialReal1dZ( final double[] coefs )
	{
		return new FunctionRealRandomAccessible<>( 1, polynomial1dZ( coefs ), DoubleType::new );
	}

	public static BiConsumer< RealLocalizable, DoubleType > polynomial1dZ( final double[] zeros )
	{
		return new BiConsumer<RealLocalizable, DoubleType >()
			{
				@Override
				public void accept( RealLocalizable p, DoubleType v )
				{
					v.setZero();
					double total = 0;
					double term = 1;
					for( int i = 0; i < zeros.length; i++ )
					{
						term = 1;
						for( int j = 0; j < i; j++ )
						{
							term *= ( p.getDoublePosition( 0 ) - zeros[ i ]);
						}
						total += term;
					}
					v.set( total );
				}
			};
	}
	public static BiConsumer< RealLocalizable, DoubleType > polynomial1dC( final double[] coefs )
	{
		return new BiConsumer<RealLocalizable, DoubleType >()
			{
				@Override
				public void accept( RealLocalizable p, DoubleType v )
				{
					v.setZero();
					double total = 0;
					double term = 0;
					for( int i = 0; i < coefs.length; i++ )
					{
						term = coefs[ i ];
						for( int j = 0; j < i; j++ )
						{
							term *= p.getDoublePosition( 0 );
						}
						total += term;
					}
					v.set( total );
				}
			};
	}

}
