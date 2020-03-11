/**
 *
 */
package org.janelia.saalfeldlab.bspline;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import org.apache.commons.io.FileUtils;

import ij.ImageJ;
import ij.gui.Plot;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessibleRealInterval;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.interpolation.randomaccess.BSplineInterpolator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * @author John Bogovic
 *
 */
public class BsplineTest1d
{

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(final String[] args) throws IOException
	{
//		new ImageJ();


		chirpWindowTest();


		System.out.println("done");
	}

	public static void chirpWindowTest() throws IOException
	{
		RandomAccessibleInterval<DoubleType> chirp = expChirpImage( 
				new DoubleType(), new FinalInterval( 256 ), 0.008, 0.15, true );


		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				3, Views.extendMirrorSingle( chirp ));


		FinalInterval subItvl = new FinalInterval( new long[]{200}, new long[]{220});

//		System.out.println( "COMPUTE FULL");
//		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( 256 );
//		coefsAlg.accept( fullCoefs );

		System.out.println( "\nCOMPUTE SUB");
		IntervalView<DoubleType> subCoefs = Views.translate( ArrayImgs.doubles( subItvl.dimension( 0 )), subItvl.min( 0 ));
		coefsAlg.accept( subCoefs );

//		System.out.println( "full coefs: ");
//		print( fullCoefs.randomAccess(), subItvl.min(0), subItvl.max(0) );
//
//		System.out.println( " ");
//		System.out.println( "sub coefs : ");
//		print( subCoefs );
//
//		System.out.println( " ");
//		System.out.println( "diffs : ");
//		printDiff( subCoefs, fullCoefs );



	}

	public static void printDiff(
			final RandomAccessibleInterval<DoubleType> a,
			final RandomAccessible<DoubleType> b)
	{
		RandomAccess<DoubleType> aAccess = a.randomAccess();
		RandomAccess<DoubleType> bAccess = b.randomAccess();

		for( long x = a.min(0); x <= a.max(0); x++ )
		{
			aAccess.setPosition( x, 0 );
			bAccess.setPosition( x, 0 );

			System.out.println( aAccess.get().getRealDouble() - bAccess.get().getRealDouble() );
		}
	}
	
	public static void print( final RandomAccessibleInterval<DoubleType> img )
	{
		print( img.randomAccess(), img.min( 0 ), img.max( 0 ));
	}

	public static void print( final RandomAccess<DoubleType> img, long start, long end ) 
	{
		for( long x = start; x <= end; x++ )
		{
			System.out.println( img.setPositionAndGet( x ));
		}
	}

	public static void chirpTest() throws IOException
	{
		RandomAccessibleInterval<DoubleType> chirp = expChirpImage( 
				new DoubleType(), new FinalInterval( 256 ), 0.008, 0.15, true );
		RandomAccess<DoubleType> chirpAccess = chirp.randomAccess();


//		toCsv( chirpAccess, 0, 255, 1, new File("/groups/saalfeld/home/bogovicj/dev/imglib/hot-knife_bogovicj/bsplineTests/chirp.csv"));

//		for( int i = 0; i < chirp.dimension(0); i++)
//		{
//			System.out.println( chirpAccess.setPositionAndGet( i ));
//		}
		

		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				3, Views.extendMirrorSingle( chirp ));

		BSplineCoefficientsInterpolator interp = new BSplineCoefficientsInterpolator( Views.extendMirrorSingle(chirp), 3 );
		
		interp.setPosition( 5.5, 0 );
		System.out.println( interp.get() );

		
		System.out.println("tocsv interp");
		toCsv( interp, 0.0, 255, 0.5, new File("/groups/saalfeld/home/bogovicj/dev/imglib/hot-knife_bogovicj/bsplineTests/chirp_interp.csv"));


//		Plot p = plot1d( chirp, "chirp", null );
//		p = plot1d( chirp, "chirp", p );
//		p.show();

		
	}
	
	public static <T extends RealType<T>> void toCsv( RandomAccess<T> access, long start, long end, long step, File f ) throws IOException
	{
		StringBuffer s = new StringBuffer();
		for( long x = start; x <= end; x += step )
		{
			access.setPosition( x, 0 );
			s.append( x );
			s.append( "," );
			s.append( access.get().getRealDouble() );
			s.append("\n");
		}
		FileUtils.writeStringToFile(f, s.toString(), StandardCharsets.UTF_8 );
	}

	public static <T extends RealType<T>> void toCsv( RealRandomAccess<T> access, double start, double end, double step, File f ) throws IOException
	{
		StringBuffer s = new StringBuffer();
		for( double x = start; x <= end; x += step )
		{
			access.setPosition( x, 0 );
			s.append( x );
			s.append( "," );
			s.append( access.get().getRealDouble() );
			s.append("\n");
		}
		FileUtils.writeStringToFile(f, s.toString(), StandardCharsets.UTF_8 );
	}
	
	public static <T extends RealType<T>> Plot plot1d( RandomAccessibleInterval<T> img1d, String type, Plot p )
	{
		double[] data = new double[ (int) img1d.dimension(0) ];

		int i = 0;
		Cursor<T> c = Views.iterable( img1d ).cursor();
		while( c.hasNext() )
			data[ i++ ] = c.next().getRealDouble();

		if ( p == null )
		{
			p = new Plot("bspline", "x", "y");
		}
		
		p.add(type, data );

		return p;
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval<T> expChirpImage( 
			final T type, final Interval interval,
			double f0, double f1, final boolean copy )
	{
		// exp chirp
		final double w = interval.realMax( 0 );
		final double k = Math.pow( (f1 / f0), 1 / w );

		BiConsumer<Localizable,T> fun = new BiConsumer<Localizable,T>(){
			@Override
			public void accept( Localizable p, T t )
			{
				t.setReal( 	1 * Math.cos( p.getDoublePosition( 0 ) * f0 * Math.pow( k, p.getDoublePosition( 0 ) ) )); 
			}
		};

		FunctionRandomAccessible<T> freqSweep = new FunctionRandomAccessible<>( interval.numDimensions(), fun, type::createVariable );
		IntervalView<T> virtualimg = Views.interval( freqSweep, interval );
		if( copy )
		{
			Img<T> memimg = Util.getSuitableImgFactory( interval, type).create(interval);
			LoopBuilder.setImages( virtualimg, memimg ).forEachPixel( (x,y) -> y.set(x) );
			return memimg;
		}
		else
			return virtualimg;
	}
	
}
