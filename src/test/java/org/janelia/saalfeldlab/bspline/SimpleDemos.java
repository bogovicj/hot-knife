package org.janelia.saalfeldlab.bspline;

import java.util.function.BiConsumer;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineLazyCoefficientsInterpolatorFactory;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.BSplineInterpolatorFactory;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class SimpleDemos
{

	public static <T extends RealType<T>> void main(String[] args)
	{
		int bsplineOrder = 3;
		Img<?> imgRaw = ImageJFunctions.wrap(IJ.openImage( args[ 0 ]));

		// Center the image at zero
		RandomAccessibleInterval<?> img = Views.translate( imgRaw, 
				new long[]{ 
					imgRaw.dimension(0) / 2, 
					imgRaw.dimension(1) / 2, 
					imgRaw.dimension(2) / 2  });

		System.out.println( "img interval: " + Util.printInterval( img ));

		/*
		 * Choose an interpolation method below:
		 */
//		RealRandomAccessible<T> realImg = interpolateOnTheFly( (RandomAccessibleInterval<T>) img, bsplineOrder );
//		RealRandomAccessible<DoubleType> realImg = interpolateCoefficients( (RandomAccessibleInterval<T>) img, bsplineOrder );
		RealRandomAccessible<DoubleType> realImg = interpolateLazyCachedCoefficients( (RandomAccessibleInterval<T>) img, bsplineOrder );
//		RealRandomAccessible<DoubleType> realImg = interpolateLazyCachedCoefficientsInfinite( (RandomAccessibleInterval<T>) img, bsplineOrder );


		/*
		 * Interpolate an infinite image using cached coefficients
		 */
		RandomAccessible<DoubleType> chirp = cosImg( new double[]{ 0.5, 0.1, 0.05 } );
		RealRandomAccessible<DoubleType> chirpRealInf = interpolateLazyCachedCoefficientsInfinite( 
				(RandomAccessible<DoubleType>)chirp, bsplineOrder );


		/*
		 * visualize everything
		 */
		BdvOptions opts = BdvOptions.options().numRenderingThreads( 4 );
		BdvStackSource<?> bdv = BdvFunctions.show( img, "img", opts );
		BdvStackSource<DoubleType> bdvReal = BdvFunctions.show( realImg, img, "interpolated", opts.addTo( bdv ));
		bdvReal.setDisplayRangeBounds( 0, 255 );
		bdvReal.setDisplayRange( 0, 255 );

		BdvStackSource<DoubleType> bdv2 = BdvFunctions.show( chirp, img, "chirp", opts.addTo( bdv ));
		bdv2.setDisplayRangeBounds( -255, 255 );
		bdv2.setDisplayRange( -1, 1 );

		BdvStackSource<DoubleType> bdv3 = BdvFunctions.show( chirpRealInf, img, "chirp interp", opts.addTo( bdv ));
		bdv3.setDisplayRangeBounds( -255, 255 );
		bdv3.setDisplayRange( -1, 1 );
	}

	public static RandomAccessible<DoubleType> cosImg( double[] freqs )
	{
		// exp chirp
		BiConsumer<Localizable,DoubleType> fun = new BiConsumer<Localizable,DoubleType>(){
			@Override
			public void accept( Localizable p, DoubleType t )
			{
//				t.setReal( 	1 * Math.cos( p.getDoublePosition( 0 ) * f0 * Math.pow( k, p.getDoublePosition( 0 ) ) )); 
				double v = 1;
				for( int d = 0; d < freqs.length; d++ )
				{
					v *= Math.cos( p.getDoublePosition( d ) * freqs[ d ] );
				}
				t.setReal( v );
			}
		};
		return new FunctionRandomAccessible<DoubleType>( 3, fun, DoubleType::new );
	}

	/**
	 * Interpolates by applying a truncated bspline kernel.  
	 * Least accurate bspline interpolation.
	 * Slowest method per-pixel.
	 * 
	 * Requires no precomputation or memory allocation.
	 */
	public static <T extends RealType<T>> RealRandomAccessible<T> interpolateOnTheFly( final RandomAccessibleInterval<T> img,
			final int order )
	{
		return Views.interpolate( Views.extendZero( img ), 
				new BSplineInterpolatorFactory<>( order ));
	}

	/**
	 * Interpolates by precomputing bspline coefficients.
	 * This method is recommended for small to medium size images.
	 * 
	 * Most accurate bspline interpolation.
	 * Fastest method per-pixel.
	 * 
	 * Allocates an new image of the specified type and of
	 * the same size as the image to be interpolated.
	 * 
	 * Precomputation covers the whole image and is O(ND)
	 * 	N - number of grid points (pixels/voxels)
	 *  D - number of dimensions
	 */
	public static <T extends RealType<T>> RealRandomAccessible<DoubleType> interpolateCoefficients( final RandomAccessibleInterval<T> img,
			final int order )
	{
		boolean clipping = false;
		DoubleType outputType = new DoubleType();

		return Views.interpolate( img,
				new BSplineCoefficientsInterpolatorFactory<>( Views.extendZero( img ), img, order, clipping, outputType ));
	}

	/**
	 * Interpolates by precomputing and cacheing bspline coefficients on demand.
	 * This method is recommended for very large images.
	 * 
	 * Intermediate accuracy bspline interpolation.
	 * Fastest method per-pixel.
	 * 
	 * Allocates new memory blockwise on demand.
	 * Precomputation covers each block.
	 */
	public static <T extends RealType<T>> RealRandomAccessible<DoubleType> interpolateLazyCachedCoefficients( final RandomAccessibleInterval<T> img,
			final int order )
	{
		boolean clipping = false;
		DoubleType outputType = new DoubleType();
		int[] blockSize = new int[]{ 64, 64, 64 };

		return Views.interpolate( img,
				new BSplineLazyCoefficientsInterpolatorFactory<>( Views.extendZero( img ), img, order, clipping, outputType, blockSize ));
	}

	/**
	 * Interpolates by precomputing and cacheing bspline coefficients on demand.
	 * This method is recommended for very large images.
	 * 
	 * Intermediate accuracy bspline interpolation.
	 * Fastest method per-pixel.
	 * 
	 * Allocates new memory blockwise on demand.
	 * Precomputation covers each block.
	 */
	public static <T extends RealType<T>> RealRandomAccessible<DoubleType> interpolateLazyCachedCoefficientsInfinite( final RandomAccessible<T> img,
			final int order )
	{
		boolean clipping = false;
		DoubleType outputType = new DoubleType();
		int[] blockSize = new int[]{ 64, 64, 64 };

		return Views.interpolate( img,
				new BSplineLazyCoefficientsInterpolatorFactory<>( img, order, clipping, outputType, blockSize ));
	}

}
