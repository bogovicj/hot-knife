/**
 *
 */
package org.janelia.saalfeldlab.bspline;

import java.io.File;
import java.io.IOException;
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
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RealRandomAccessibleRealInterval;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorEven;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.interpolation.randomaccess.BSplineInterpolator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
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

		constTest( 2 );
		System.out.println("\n#######################\n");

//		linTest( 3 );
//		System.out.println("\n#######################\n");

//		quadTestV2( 3 );
//		System.out.println("\n#######################\n");

//		cubTest( 3 );
//		System.out.println("\n#######################\n");

//		quadTest( 3 );

//		sinWindowTest();

//		chirpWindowTest();

//		oneOffTest();

		System.out.println("done");
	}
	
	public static void oneOffTest()
	{
		RandomAccessibleInterval< DoubleType > p0 = ConstantUtils.constantRandomAccessibleInterval( new DoubleType( 1 ), new FinalInterval( 32 ));

		BSplineCoefficientsInterpolator<DoubleType> spline2p0 = new BSplineCoefficientsInterpolatorFactory( p0, 2  ).create( p0 );

		spline2p0.setPosition( 15.55, 0 );
		System.out.println( spline2p0.get() );
	}

	public static void cubTest( int order ) throws IOException
	{

//		final double center = 15;
		final double center = 128;

		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				double x = p.getDoublePosition(0) - center ;
				t.setReal(  x*x*x - x );
			}
		};

		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

//		FinalInterval fullItvl = new FinalInterval( 32 );
//		interpVsReal(realImg, order, fullItvl, 13, 17, 1 );

		FinalInterval fullItvl = new FinalInterval( 256 );
		interpVsReal(realImg, order, fullItvl, 126, 129, 0.1 );
	}

	public static void quadTestV2( int order ) throws IOException
	{

//		final double center = 15;
		final double center = 128;

		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				double x = p.getDoublePosition(0) - center ;
				t.setReal(  x*x  - 1 );
			}
		};

		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

//		FinalInterval fullItvl = new FinalInterval( 32 );
//		interpVsReal(realImg, order, fullItvl, 13, 17, 1 );

		FinalInterval fullItvl = new FinalInterval( 256 );
		interpVsReal(realImg, order, fullItvl, 127, 128, 0.1 );
	}
	
	public static void quadTest( int order ) throws IOException
	{
		final double a = -1;
		final double b = 8;
		final double c = -15;
		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				double x = p.getDoublePosition(0);
				t.setReal( a * x * x + b * x + c );
			}
		};
		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

		double step = 0.2;
		FinalInterval fullItvl = new FinalInterval( 32 );

		interpVsReal(realImg, order, fullItvl, 3, 5, 0.2 );
	}

	public static void linTest( int order ) throws IOException
	{
		double offset = 0;
		double slope = 1;	

		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				t.setReal( offset + slope * p.getDoublePosition( 0 ));
			}
		};
		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

//		FinalInterval fullItvl = new FinalInterval( 32 );
//		interpVsReal(realImg, order, fullItvl, 3, 5, 0.2 );

		FinalInterval fullItvl = new FinalInterval( 256 );
		interpVsReal(realImg, order, fullItvl, 126, 128, 0.2 );
	}

	public static void impulseTest( int order ) throws IOException
	{
		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				if( p.getDoublePosition( 0 ) == 0 )
					t.setOne();
				else
					t.setZero();
			}
		};
		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

		double step = 0.2;
		FinalInterval fullItvl = new FinalInterval( 32 );

		interpVsReal(realImg, order, fullItvl, -1, 1, 0.1 );
	}
	
	public static void constTest( int order ) throws IOException
	{
		BiConsumer<RealLocalizable,DoubleType> fun = new BiConsumer<RealLocalizable,DoubleType>(){
			@Override
			public void accept( RealLocalizable p, DoubleType t )
			{
				t.setReal( 1.0 );
			}
		};
		RealRandomAccessible<DoubleType> realImg = funRealImage( new DoubleType(), 1, fun );

		FinalInterval fullItvl = new FinalInterval( 32 );

		interpVsReal(realImg, order, fullItvl, 15, 17, 0.1 );
//		newThing( realImg, fullItvl );
	}
	
	public static void newThing( RealRandomAccessible<DoubleType> realImg, Interval fullItvl )
	{
		IntervalView<DoubleType> img = Views.interval( Views.raster( realImg ), fullItvl );

		int order = 2;
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				order, Views.extendMirrorSingle( img ));

		System.out.println( "COMPUTE COEFS" );
		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( fullItvl.dimension( 0 ));
		coefsAlg.accept( fullCoefs );
		
//		BSplineCoefficientsInterpolatorEven<DoubleType> interp = new BSplineCoefficientsInterpolatorEven<>( 
//				fullCoefs, order, new DoubleType() );

		BSplineCoefficientsInterpolator<DoubleType> interp = BSplineCoefficientsInterpolator.build( 
				order, Views.extendZero( fullCoefs ), new DoubleType() );
		
		interp.setPosition(16.2, 0);
		System.out.println( interp.get());
		interp.setPosition(16.8, 0);
		System.out.println( interp.get());
	}

	public static void linTestFvS( int order ) throws IOException
	{
		double offset = 0;
		double slope = 1;	

		BiConsumer<Localizable,DoubleType> fun = new BiConsumer<Localizable,DoubleType>(){
			@Override
			public void accept( Localizable p, DoubleType t )
			{
				t.setReal( offset + slope * p.getDoublePosition( 0 ));
			}
		};

		FinalInterval fullItvl = new FinalInterval( 32 );
		FinalInterval subItvl = new FinalInterval( new long[]{ 12, 24 });

		RandomAccessibleInterval<DoubleType> img = funImage( new DoubleType(), fullItvl, fun, false );

		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				order, Views.extendMirrorSingle( img ));

		System.out.println( " ");
		System.out.println( "COMPUTE SUB");

		IntervalView<DoubleType> subCoefs = Views.translate( ArrayImgs.doubles( subItvl.dimension( 0 )), subItvl.min( 0 ));
		coefsAlg.accept( subCoefs );

		BSplineCoefficientsInterpolator<DoubleType> subInterp = BSplineCoefficientsInterpolator.build(
				order, Views.extendMirrorSingle( subCoefs ), new DoubleType() );

		subInterp.setPosition( 16, 0);
		System.out.println( " val at 16 : " + subInterp.get() );
		
//		System.out.println( " ");
//		System.out.println( "sub interp img : ");
//		print( subInterp, subItvl.realMin(0), subItvl.realMax(0), 1.0 );

//		fullVsSub( img, subItvl );
	}
	
	public static void constTestFvS( int order ) throws IOException
	{
		IntervalView<DoubleType> img = Views.interval(
				ConstantUtils.constantRandomAccessible(new DoubleType(1), 1),
				new FinalInterval( 256 ));

		FinalInterval subItvl = new FinalInterval( new long[]{24, 48});
		fullVsSub( img, subItvl, order );

	}

	public static void sinWindowTest() throws IOException
	{
		RandomAccessibleInterval<DoubleType> sin = sinImage( 
				new DoubleType(), new FinalInterval( 64 ), 1, 2, true );

		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( sin );
		BSplineCoefficientsInterpolatorFactory<DoubleType,DoubleType> f = new BSplineCoefficientsInterpolatorFactory<>( ext, sin , 3);
		RealRandomAccess<DoubleType> interp = f.create( ext );
		
		// old
//		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
//				3, Views.extendMirrorSingle( sin ));
//		System.out.println( "COMPUTE FULL");
//		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( 256 );
//		coefsAlg.accept( fullCoefs );
//		BSplineCoefficientsInterpolator<DoubleType,DoubleType> interp = new BSplineCoefficientsInterpolator<>( 
//				Views.extendMirrorSingle( fullCoefs ), 3 );

		print( sin );
		System.out.println( " ");
		
		print( interp, 0.0, 64.0, 0.5 );

	}

	public static void interpVsReal( final RealRandomAccessible<DoubleType> realImg, final int order,
			final Interval fullItvl, double min, double max, double step )

	{
		IntervalView<DoubleType> img = Views.interval( Views.raster( realImg ), fullItvl );

		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				order, Views.extendMirrorSingle( img ));

		System.out.println( "COMPUTE COEFS" );
		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( fullItvl.dimension( 0 ));
		coefsAlg.accept( fullCoefs );
		
		//Views.extendZero( fullCoefs );
		
		BSplineCoefficientsInterpolator<DoubleType> interp = BSplineCoefficientsInterpolator.build(
				order, fullCoefs, new DoubleType() );

		interp.setPosition( 16.2, 0 );
		System.out.println( " f(16.2) = " + interp.get());

		System.out.println( " " );
		System.out.println( " " );
		System.out.println( " " );

		interp.setPosition( 16.8, 0 );
		System.out.println( " f(16.8) = " + interp.get());

//		System.out.println( " " );
//		System.out.println( "all coefs: " );
//		print( fullCoefs );

//
//		System.out.println( " " );
//		System.out.println( "coefs: " );
//		print( fullCoefs.randomAccess(), (long)Math.round(min), (long)Math.round(max) );
//
//		System.out.println( " " );
//		System.out.println( "real: " );
//		print( realImg.realRandomAccess(), min, max, step);
//
//		System.out.println( " " );
//		System.out.println( "interp: " );
//		print( interp, min, max, step );
		

//		System.out.println( " " );
//		System.out.println( "diff: " );
//		printDiff( realImg.realRandomAccess(), interp, min, max, step );
	}
	
	public static void fullVsSub( final RandomAccessibleInterval<DoubleType> img, final Interval subItvl, final int order )
	{
		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				order, Views.extendMirrorSingle( img ));

		System.out.println( "COMPUTE FULL");
		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( 256 );
		coefsAlg.accept( fullCoefs );

		System.out.println( "\nCOMPUTE SUB");
		IntervalView<DoubleType> subCoefs = Views.translate( ArrayImgs.doubles( subItvl.dimension( 0 )), subItvl.min( 0 ));
		coefsAlg.accept( subCoefs );
		
		System.out.println( "full coefs: ");
		print( fullCoefs.randomAccess(), subItvl.min(0), subItvl.max(0) );

		System.out.println( " ");
		System.out.println( "sub coefs : ");
		print( subCoefs );

		System.out.println( " ");
		System.out.println( "coef diffs : ");
		printDiff( subCoefs, fullCoefs );


		BSplineCoefficientsInterpolator<DoubleType> fullInterp = BSplineCoefficientsInterpolator.build(
				order, Views.extendMirrorSingle( fullCoefs ), new DoubleType() );

		BSplineCoefficientsInterpolator<DoubleType> subInterp = BSplineCoefficientsInterpolator.build(
				order, Views.extendMirrorSingle( subCoefs ), new DoubleType() );
		
		System.out.println( " ");
		System.out.println( "full interp img: ");
		print( fullInterp, subItvl.realMin(0), subItvl.realMax(0), 1.0 );

		System.out.println( " ");
		System.out.println( "sub interp img : ");
		print( subInterp, subItvl.realMin(0), subItvl.realMax(0), 1.0 );

		System.out.println( " ");
		System.out.println( "img diffs : ");
		printDiff( fullInterp, subInterp, subItvl.realMin(0), subItvl.realMax(0), 1.0 );
	}

	public static void chirpWindowTest() throws IOException
	{
		RandomAccessibleInterval<DoubleType> chirp = expChirpImage( 
				new DoubleType(), new FinalInterval( 256 ), 0.008, 0.15, true );


		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 
				3, Views.extendMirrorSingle( chirp ));


		FinalInterval subItvl = new FinalInterval( new long[]{50}, new long[]{70});

		System.out.println( "COMPUTE FULL");
		ArrayImg<DoubleType, DoubleArray> fullCoefs = ArrayImgs.doubles( 256 );
		coefsAlg.accept( fullCoefs );

		toCsv( fullCoefs.randomAccess(), 0, 255, 1, new File("/home/john/tests/bspline/chirp_full.csv"));

		System.out.println( "\nCOMPUTE SUB");
		IntervalView<DoubleType> subCoefs = Views.translate( ArrayImgs.doubles( subItvl.dimension( 0 )), subItvl.min( 0 ));
		coefsAlg.accept( subCoefs );


//		toCsv( fullCoefs, new File("/home/john/tests/bspline/chirp_full.csv"));
//		toCsv( subCoefs, new File("/home/john/tests/bspline/chirp_sub.csv"));


		System.out.println( "full coefs: ");
		print( fullCoefs.randomAccess(), subItvl.min(0), subItvl.max(0) );

		System.out.println( " ");
		System.out.println( "sub coefs : ");
		print( subCoefs );

		System.out.println( " ");
		System.out.println( "diffs : ");
		printDiff( subCoefs, fullCoefs );


	}

	public static void printDiff(
			final RealRandomAccess<DoubleType> aAccess,
			final RealRandomAccess<DoubleType> bAccess,
			double start, double end, double step )
	{
//		RealRandomAccess<DoubleType> aAccess = a.realRandomAccess()
//		RealRandomAccess<DoubleType> bAccess = b.realRandomAccess();

		for( double x = start; x <= end; x += step )
		{
			aAccess.setPosition( x, 0 );
			bAccess.setPosition( x, 0 );

			System.out.println( aAccess.get().getRealDouble() - bAccess.get().getRealDouble() );
		}
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
		for( long x = start; x <= end+0.0001; x++ )
		{
			System.out.println( img.setPositionAndGet( x ));
		}
	}

	public static void print( final RealRandomAccess<DoubleType> img, double start, double end, double step ) 
	{
		for( double x = start; x <= end+0.0001; x += step )
		{
			System.out.println( x + " " + img.setPositionAndGet( x ));
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

		BSplineCoefficientsInterpolator interp = BSplineCoefficientsInterpolator.build(
				3, Views.extendMirrorSingle(chirp), new DoubleType() );
		
		interp.setPosition( 5.5, 0 );
		System.out.println( interp.get() );

		
		System.out.println("tocsv interp");
		toCsv( interp, 0.0, 255, 0.5, new File("/groups/saalfeld/home/bogovicj/dev/imglib/hot-knife_bogovicj/bsplineTests/chirp_interp.csv"));


//		Plot p = plot1d( chirp, "chirp", null );
//		p = plot1d( chirp, "chirp", p );
//		p.show();

		
	}

	public static <T extends RealType<T>> void toCsv( RandomAccessibleInterval<T> access, File f ) throws IOException
	{
		toCsv( access.randomAccess(), access.min(0), access.max(0), 1, f );
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

	public static <T extends RealType<T>> RandomAccessibleInterval<T> sinImage(
			final T type, final Interval interval, double amplitude, double nPeriods, final boolean copy )
	{

		final double twoPi = 2 * Math.PI;
		final double w = interval.realMax( 0 ) - interval.realMin( 0 );
		final double freq = twoPi * nPeriods / w;

		BiConsumer<Localizable,T> fun = new BiConsumer<Localizable,T>(){
			@Override
			public void accept( Localizable p, T t )
			{
				t.setReal( amplitude * Math.sin( freq * p.getDoublePosition( 0 )));
			}
		};

		return funImage( type, interval, fun, copy );
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

		return funImage( type, interval, fun, copy );
	}
	

	public static <T extends RealType<T>> RealRandomAccessible<T> funRealImage( 
			final T type, int nd,
			BiConsumer<RealLocalizable,T> fun )
	{
		return new FunctionRealRandomAccessible<>( nd, fun, type::createVariable );
	}

	public static <T extends RealType<T>> RandomAccessibleInterval<T> funImage( 
			final T type, final Interval interval,
			BiConsumer<Localizable,T> fun, final boolean copy )
	{
		FunctionRandomAccessible<T> funImg = new FunctionRandomAccessible<>( interval.numDimensions(), fun, type::createVariable );
		IntervalView<T> virtualimg = Views.interval( funImg, interval );
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
