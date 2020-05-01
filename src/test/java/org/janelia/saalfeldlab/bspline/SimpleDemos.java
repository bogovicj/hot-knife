package org.janelia.saalfeldlab.bspline;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineLazyCoefficientsInterpolatorFactory;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.BSplineInterpolatorFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class SimpleDemos
{

	public static <T extends RealType<T>> void main(String[] args)
	{
		int bsplineOrder = 3;
		Img<?> img = ImageJFunctions.wrap(IJ.openImage( args[ 0 ]));

//		RealRandomAccessible<T> realImg = interpolateOnTheFly( (RandomAccessibleInterval<T>) img, bsplineOrder );
//		RealRandomAccessible<DoubleType> realImg = interpolateCoefficients( (RandomAccessibleInterval<T>) img, bsplineOrder );
		RealRandomAccessible<DoubleType> realImg = interpolateLazyCachedCoefficients( (RandomAccessibleInterval<T>) img, bsplineOrder );

		BdvOptions opts = BdvOptions.options().numRenderingThreads( 4 );
		BdvStackSource<?> bdv = BdvFunctions.show( img, "img", opts );
		BdvFunctions.show( realImg, img, "interpolated", opts.addTo( bdv ));

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
	 * Allocates new memory
	 * the same size as the image to be interpolated.
	 * 
	 * Precomputation covers the whole image and is O(ND)
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

}
