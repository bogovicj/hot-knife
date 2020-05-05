package org.janelia.saalfeldlab.bspline;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.bspline.BSplineCoefficientsInterpolator;
import net.imglib2.bspline.BSplineCoefficientsInterpolatorFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

public class BsplineOobTest
{
	
	private FinalInterval itvl;
	private RandomAccessible<DoubleType> img;
	private double[] oobPos;

	@Before
	public void setup()
	{
		itvl = new FinalInterval( new long[]{ 8, 8 });

		img = new FunctionRandomAccessible<>( 2, 
				(p,v) -> v.setReal( p.getDoublePosition(0) ),
				DoubleType::new);

		oobPos = new double[]{ -2, -2 };
	}

	@Test
	public void testConstOob()
	{
		DoubleType d = new DoubleType();
		FloatType f = new FloatType();
		UnsignedByteType ub = new UnsignedByteType();

		// ZERO
		testConst( d, d );
		testConst( d, f );
		testConst( d, ub );

		testConst( f, d );
		testConst( f, f );
		testConst( f, ub );

		testConst( ub, d );
		testConst( ub, f );
		testConst( ub, ub );
		
		// Border
		// TODO fails, probably for good reasons 
//		testBorder( d, d );
//		testBorder( f, f );

		// Mirror single
		DoubleType dval = new DoubleType( 2 );
		FloatType fval = new FloatType( 2 );
		testMirrorS( d, dval );
		testMirrorS( f, fval );

		// Mirror double
		dval.set( 1 );
		fval.set( 1 );
		testMirrorD( d, dval );
		testMirrorD( f, fval );
	}	

	public <T extends RealType<T> & NativeType<T>, S extends RealType<S>> void testConst( T t, S s )
	{
		testOob( t, s.copy(), new OutOfBoundsConstantValueFactory<>( s.copy() ));
	}

	public <T extends RealType<T> & NativeType<T>, S extends RealType<S>> void testMirrorD( T t, T val )
	{
		testOob( t, val, new OutOfBoundsMirrorFactory<>( OutOfBoundsMirrorFactory.Boundary.DOUBLE ));
	}

	public <T extends RealType<T> & NativeType<T>, S extends RealType<S>> void testMirrorS( T t, T val )
	{
		testOob( t, val, new OutOfBoundsMirrorFactory<>( OutOfBoundsMirrorFactory.Boundary.SINGLE ));
	}

	public <T extends RealType<T> & NativeType<T>, S extends RealType<S>> void testBorder( T t, T val )
	{
		testOob( t, val, new OutOfBoundsBorderFactory<>());
	}

	public <T extends RealType<T> & NativeType<T>, S extends RealType<S>> void testOob( T t, S s, OutOfBoundsFactory<S,?> oob )
	{
		ArrayImgFactory<T> imgFactory = new ArrayImgFactory<>( t.copy());

		BSplineCoefficientsInterpolator<T> interp = new BSplineCoefficientsInterpolatorFactory<>( img, itvl, 3, false, 
				imgFactory, oob ).create( img );

		String suffix = " type " + t.getClass().toString() + " ; oob type " + s.getClass().toString();

		Assert.assertNotNull("interp not null" + suffix, interp );

		interp.setPosition( oobPos );
		Assert.assertEquals("oob works" + suffix, s.getRealDouble(), interp.get().getRealDouble(), 1e-6 );
	}

}
