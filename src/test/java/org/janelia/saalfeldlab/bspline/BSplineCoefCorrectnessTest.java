package org.janelia.saalfeldlab.bspline;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.interpolation.randomaccess.BSplineInterpolator;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.view.Views;

public class BSplineCoefCorrectnessTest
{

	@Test
	public void test2Spline1d()
	{
		RandomAccessibleInterval< DoubleType > p0 = ConstantUtils.constantRandomAccessibleInterval( new DoubleType( 1 ), new FinalInterval( 32 ));

		BSplineCoefficientsInterpolator<DoubleType> spline2p0 = new BSplineCoefficientsInterpolatorFactory( p0, 2  ).create( p0 );

		spline2p0.setPosition( 15.5, 0 );
		System.out.println( spline2p0.get() );
		Assert.assertEquals("spline2 p0", 1.0, spline2p0.get().getRealDouble(), 1e-4 );

		RandomAccessibleInterval<DoubleType> p1 = Views.interval( 
				polynomialImg1dC( new double[]{ 1, 1 }), new FinalInterval( 32 ));

		BSplineCoefficientsInterpolator<DoubleType> spline2p1 = new BSplineCoefficientsInterpolatorFactory( p1, 2 ).create( p1 );

		spline2p1.setPosition( 15.5, 0 );
		System.out.println( spline2p1.get() );
		Assert.assertEquals("spline2 p1", 16.5, spline2p1.get().getRealDouble(), 0.1 );

	}

	@Test
	public void test3Spline1d()
	{
		RandomAccessibleInterval< DoubleType > p0 = ConstantUtils.constantRandomAccessibleInterval( new DoubleType( 1 ), new FinalInterval( 32 ));

		BSplineCoefficientsInterpolator<DoubleType> spline3p0 = new BSplineCoefficientsInterpolatorFactory( p0, 3  ).create( p0 );

		spline3p0.setPosition( 15.5, 0 );
		System.out.println( spline3p0.get() );
		Assert.assertEquals("spline3 p0", 1.0, spline3p0.get().getRealDouble(), 1e-6 );

		RandomAccessibleInterval<DoubleType> p1 = Views.interval( 
				polynomialImg1dC( new double[]{ 1, 1 }), new FinalInterval( 32 ));

		BSplineCoefficientsInterpolator<DoubleType> spline3p1 = new BSplineCoefficientsInterpolatorFactory( p1, 3 ).create( p1 );

		spline3p1.setPosition( 15.5, 0 );
		System.out.println( spline3p1.get() );
		Assert.assertEquals("spline3 p1", 16.5, spline3p1.get().getRealDouble(), 0.1 );

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
