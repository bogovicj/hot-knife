/**
 *
 */
package org.janelia.saalfeldlab.bspline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.hotknife.ops.Max;
import org.janelia.saalfeldlab.hotknife.ops.Multiply;
import org.janelia.saalfeldlab.hotknife.util.Lazy;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class LazyBSpline {

	private static int[] blockSize = new int[] {32, 32, 32};

	private static int[] blockSizeSmall = new int[] {10, 10, 10 };

	/**
	 * @param args
	 */
	public static void main(final String[] args)
	{

		new ImageJ();

		RandomAccessibleInterval<DoubleType> img = flyImg();
//		RandomAccessibleInterval<DoubleType> img = humanImg();
//		RandomAccessibleInterval<DoubleType> img = impulseImg( new long[]{ 100, 100 }, new long[]{ 5, 5 });
//		RandomAccessibleInterval<DoubleType> img = impulseImg( new long[]{ 100, 100 }, new long[]{ 9, 9 });
		
		runVisTest( img, blockSizeSmall, true );
		
//		ArrayList<double[]> pts = new ArrayList<>();
//		pts.add( new double[]{ 9.5, 9.5 } );
//		runPtTest( img, blockSizeSmall, pts );

//		runPadTest( img, blockSizeSmall );
	}

	public static RandomAccessibleInterval<DoubleType> flyImg()
	{
		final ImagePlus imp = IJ.openImage( "/Users/bogovicj/Documents/projects/jrc2018/jrc18_demo_sample_data/JRC2018_FEMALE_small.tif" );
		System.out.println( "imp: " + imp );
		imp.show();

		final RandomAccessibleInterval<? extends RealType<?>> img = (RandomAccessibleInterval<? extends RealType<?>>) ImagePlusImgs.from(imp);
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());
		return imgDouble;
	}

	public static RandomAccessibleInterval<DoubleType> humanImg()
	{
		final ImagePlus imp = IJ.openImage( "/home/john/tmp/t1-head.tif" );
		imp.show();

		final RandomAccessibleInterval<? extends RealType<?>> img = (RandomAccessibleInterval<? extends RealType<?>>) ImagePlusImgs.from(imp);
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());
		return imgDouble;
	}
	
	public static RandomAccessibleInterval<DoubleType> impulseImg( long[] size, long[] impulsePos )
	{
		BiConsumer<Localizable,DoubleType> fun = new BiConsumer<Localizable,DoubleType>(){
			@Override
			public void accept( Localizable p, DoubleType t )
			{
				for( int i = 0; i < p.numDimensions(); i++ )
					if( p.getLongPosition( i ) != impulsePos[ i ])
					{
						t.setZero();
						return;
					}

				t.setOne();
			}
		};
		return BsplineTest1d.funImage( new DoubleType(), new FinalInterval( size ), fun, true );
	}

	public static <T extends RealType<T>> void runPadTest(
			final RandomAccessibleInterval<T> img,
			final int[] blockSize )
	{
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());

		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( imgDouble );
		
//		// not lazy
//		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
//		RandomAccessibleInterval<DoubleType> coefStorage = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
//		coefsAlg.accept( coefStorage );
//		BSplineCoefficientsInterpolator coefsImg = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorage ), 3 );
//		RealRandomAccessible<DoubleType> interpImg = Views.interpolate( imgDouble, coefsImg );
//
//		// Lazy bspline coefficients Has artifacts
//		final BSplineDecomposition<DoubleType,DoubleType> coefsAlgForLazy = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
//		final RandomAccessibleInterval<DoubleType> coefStorageLazy = Lazy.process(
//				imgDouble,
//				blockSize,
//				new DoubleType(),
//				AccessFlags.setOf( AccessFlags.VOLATILE ),
//				coefsAlgForLazy);
//
//		BSplineCoefficientsInterpolator coefsImgLazy = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorageLazy ), 3 );
//		RealRandomAccessible<DoubleType> interpImgLazy = Views.interpolate( 
//				imgDouble, coefsImgLazy );

		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
		ArrayImg<DoubleType, DoubleArray> coefsBlockImg = ArrayImgs.doubles( 10, 10 );
		
		IntervalView<DoubleType> coefsBlock = Views.translate( coefsBlockImg, 10, 10 );
		coefsAlg.accept( coefsBlock );

//		IntervalView<DoubleType> coefsBlock = Views.translate( coefsBlockImg, 10, 10 );
//		coefsAlg.acceptPadded( coefsBlock );

		BdvOptions options = BdvOptions.options().numRenderingThreads( 2 );
		final BdvStackSource<DoubleType> imgSrc = BdvFunctions.show( coefsBlock, "coefs", options );
		imgSrc.setDisplayRangeBounds( -3, 3 );

	}
	
	public static <T extends RealType<T>> void runPtTest(
			final RandomAccessibleInterval<T> img,
			final int[] blockSize,
			final List<double[]> ptList )
	{
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());

		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( imgDouble );
		
		// not lazy
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
		RandomAccessibleInterval<DoubleType> coefStorage = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
		coefsAlg.accept( coefStorage );
		BSplineCoefficientsInterpolator coefsImg = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorage ), 3 );
		RealRandomAccessible<DoubleType> interpImg = Views.interpolate( imgDouble, coefsImg );

		// Lazy bspline coefficients Has artifacts
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlgForLazy = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
		final RandomAccessibleInterval<DoubleType> coefStorageLazy = Lazy.process(
				imgDouble,
				blockSize,
				new DoubleType(),
				AccessFlags.setOf( AccessFlags.VOLATILE ),
				coefsAlgForLazy);
		BSplineCoefficientsInterpolator coefsImgLazy = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorageLazy ), 3 );
		RealRandomAccessible<DoubleType> interpImgLazy = Views.interpolate( 
				imgDouble, coefsImgLazy );
		

		RealRandomAccess<DoubleType> rAccess = interpImg.realRandomAccess();
		RealRandomAccess<DoubleType> rLazyAccess = interpImgLazy.realRandomAccess();

		for( double[] pt : ptList )
		{
			rAccess.setPosition( pt );
			rLazyAccess.setPosition( pt );

			System.out.println( "pt  : " + Arrays.toString( pt ));
//			System.out.println( "orig: " + rAccess.get() );
			System.out.println( "lazy: " + rLazyAccess.get() );
			System.out.println( " " );
		}

	}
	
	public static <T extends RealType<T>> void runVisTest( 
			final RandomAccessibleInterval<T> img,
			final int[] blockSize,
			final boolean showCoefs )
	{
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());

		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( imgDouble );

		// LOOKING GOOD
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );

		RandomAccessibleInterval<DoubleType> coefStorage = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
		coefsAlg.accept( coefStorage );

		BSplineCoefficientsInterpolator coefsImg = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorage ), 3 );
		RealRandomAccessible<DoubleType> interpImg = Views.interpolate( imgDouble, coefsImg );

		// Lazy bspline coefficients Has artifacts
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlgForLazy = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );

		final RandomAccessibleInterval<DoubleType> coefStorageLazy = Lazy.process(
				imgDouble,
				blockSize,
				new DoubleType(),
				AccessFlags.setOf( AccessFlags.VOLATILE ),
				coefsAlgForLazy);

		BSplineCoefficientsInterpolator coefsImgLazy = new BSplineCoefficientsInterpolator( Views.extendZero( coefStorageLazy ), 3 );
		RealRandomAccessible<DoubleType> interpImgLazy = Views.interpolate( 
				imgDouble, coefsImgLazy );
		
//		Point p = new Point( 3 ); 
//		p.setPosition(new int[]{ 250, 75, 100 });
//
//		ArrayRandomAccess<DoubleType> coefAccess = coefImg.randomAccess();
//		coefAccess.setPosition( p );
//		System.out.println( coefAccess.get() );
//
//		interpImg.setPosition( p );
//		System.out.println( interpImg.get() );
	
	

		BdvOptions options = BdvOptions.options().numRenderingThreads( 2 );
		final BdvStackSource<T> imgSrc = BdvFunctions.show( img, "img", options );
		
		final BdvStackSource<DoubleType> imgReal =
				BdvFunctions.show( interpImg, img,
						"img interp",
						options.addTo( imgSrc ));

		final BdvStackSource<DoubleType> imgRealLazy =
				BdvFunctions.show( interpImgLazy, img,
						"img interp lazy",
						options.addTo( imgSrc ));
	
		if( showCoefs )
		{
			BdvStackSource< DoubleType > coefSrc =
					BdvFunctions.show(
							coefStorage,
							"coefs",
							options.addTo( imgReal ));

			BdvStackSource< DoubleType > coefSrcLazy =
					BdvFunctions.show(
							coefStorageLazy,
							"coefs lazy",
							options.addTo( imgReal ));
		}
		
	}

	public static void oldMain(final String[] args)
	{
//		final ImagePlus imp = IJ.openImage( "/groups/saalfeld/public/jrc2018/small_sample_data/JRC2018_FEMALE_small.tif" );
		final ImagePlus imp = IJ.openImage( "/home/john/tmp/t1-head.tif" );

		imp.show();

		final RandomAccessibleInterval<? extends RealType<?>> img = (RandomAccessibleInterval<? extends RealType<?>>) ImagePlusImgs.from(imp);
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());
		
		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( imgDouble );


//		// not lazy
//		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
//				3, ext );
//		ArrayImg<DoubleType, DoubleArray> coefImg = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
//		coefsAlg.accept( coefImg );
//		System.out.println( "done" );


//		
//		// not lazy ( subset )
//		final BSplineDecomposition<DoubleType,DoubleType> coefsAlgSub = new BSplineDecomposition<DoubleType,DoubleType>( 
//				3, ext );
//		ArrayImg<DoubleType, DoubleArray> coefSubset = ArrayImgs.doubles( 50, 50, 50 );
//		IntervalView<DoubleType> coefOffset = Views.translate( coefSubset, 200, 50, 50 );
//
//		ArrayImg<DoubleType, DoubleArray> coefSubImg = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
//		coefsAlgSub.accept( coefOffset );
//		System.out.println( "done" );


		final BSplineDecomposition<DoubleType,DoubleType> coefsAlgForLazy = new BSplineDecomposition<DoubleType,DoubleType>( 
				3, ext );
		// lazy
		final RandomAccessibleInterval<DoubleType> coefImgLazy = Lazy.process(
				img,
				blockSize,
				new DoubleType(),
				AccessFlags.setOf( AccessFlags.VOLATILE ),
				coefsAlgForLazy);
		
//		RealRandomAccessible<DoubleType> interpImg = Views.interpolate( 
//				Views.extendZero( coefImg ), new BSplineCoefficientsInterpolatorFactory( 3 ));

		RealRandomAccessible<DoubleType> interpImg = Views.interpolate( 
				Views.extendZero( coefImgLazy ), new BSplineCoefficientsInterpolatorFactory( 3 ));
		

		// LOOKING GOOD
//		BSplineCoefficientsInterpolator interpImg = new BSplineCoefficientsInterpolator(coefImg, 3);
//		
//		Point p = new Point( 3 ); 
//		p.setPosition(new int[]{ 250, 75, 100 });
//
//		ArrayRandomAccess<DoubleType> coefAccess = coefImg.randomAccess();
//		coefAccess.setPosition( p );
//		System.out.println( coefAccess.get() );
//
//		interpImg.setPosition( p );
//		System.out.println( interpImg.get() );
	
	
	
		BdvOptions options = BdvOptions.options().numRenderingThreads( 24 );
	
		final BdvStackSource<DoubleType> imgSrc =
				BdvFunctions.show(
						imgDouble,
						"img");
		
		final BdvStackSource<DoubleType> imgReal =
				BdvFunctions.show(
						interpImg,
						imgDouble,
						"img interp",
						options.addTo( imgSrc ));

		
//		BdvStackSource< DoubleType > coefSrc =
//				BdvFunctions.show(
//						coefImg,
//						"coefs",
//						options.addTo( imgReal ));
		


//		BdvStackSource< DoubleType > coefSubSrc =
//				BdvFunctions.show(
//						coefOffset,
//						"coefs offset",
//						options.addTo( imgSrc ) );
		


//		BdvStackSource< Volatile<DoubleType> > coefSrcLazy =
//				BdvFunctions.show(
//						VolatileViews.wrapAsVolatile( coefImgLazy ),
//						"coefs Lazy",
//						options.addTo( imgSrc ) );
		
		

		
	}

}
