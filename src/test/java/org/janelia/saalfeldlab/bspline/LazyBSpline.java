/**
 *
 */
package org.janelia.saalfeldlab.bspline;

import java.util.ArrayList;
import java.util.Arrays;

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
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.realtransform.AffineTransform3D;
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

	/**
	 * @param args
	 */
	public static void main(final String[] args) {

		new ImageJ();


		final ImagePlus imp = IJ.openImage( "/groups/saalfeld/public/jrc2018/small_sample_data/JRC2018_FEMALE_small.tif" );
		imp.show();

		final RandomAccessibleInterval<UnsignedByteType> img = ImagePlusImgs.from(imp);
		final RandomAccessibleInterval<DoubleType> imgDouble =
				Converters.convert(
						img,
						(a, b) -> { b.set(a.getRealDouble()); },
						new DoubleType());
		
		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( imgDouble );
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				3, ext );

		// not lazy
		ArrayImg<DoubleType, DoubleArray> coefImg = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
		coefsAlg.accept( coefImg );
		System.out.println( "done" );
		
		// not lazy ( subset )
		ArrayImg<DoubleType, DoubleArray> coefSubset = ArrayImgs.doubles( 50, 50, 50 );
		IntervalView<DoubleType> coefOffset = Views.translate( coefSubset, 100, 50, 50 );

		ArrayImg<DoubleType, DoubleArray> coefSubImg = ArrayImgs.doubles( Intervals.dimensionsAsLongArray( img ));
		coefsAlg.accept( coefOffset );
		System.out.println( "done" );

		// lazy
		final RandomAccessibleInterval<DoubleType> coefImgLazy = Lazy.process(
				img,
				blockSize,
				new DoubleType(),
				AccessFlags.setOf( AccessFlags.VOLATILE ),
				coefsAlg);


		BdvOptions options = BdvOptions.options().numRenderingThreads( 24 );
	
		final BdvStackSource<DoubleType> imgSrc =
				BdvFunctions.show(
						imgDouble,
						"img");
		imgSrc.setDisplayRange( 0, 255 );
		
		BdvStackSource< DoubleType > coefSrc =
				BdvFunctions.show(
						coefImg,
						"coefs",
						options.addTo( imgSrc ) );

		BdvStackSource< DoubleType > coefSubSrc =
				BdvFunctions.show(
						coefOffset,
						"coefs offset",
						options.addTo( imgSrc ) );

//		BdvStackSource< Volatile<DoubleType> > coefSrcLazy =
//				BdvFunctions.show(
//						VolatileViews.wrapAsVolatile( coefImgLazy ),
//						"coefs Lazy",
//						options.addTo( imgSrc ) );

	}
}
