package org.janelia.saalfeldlab.bspline;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.bspline.BSplineDecomposition;

public class TestSplinePolesGains
{
	public static final double delta = 1e-6;
	
	@Test
	public void test0()
	{
		double[] poles = BSplineDecomposition.poles( 0 );
		Assert.assertEquals("deg zero : num poles", 0, poles.length );

		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg zero : gain", 1, c0, delta );
	}

	@Test
	public void test1()
	{
		double[] poles = BSplineDecomposition.poles( 1 );
		Assert.assertEquals("deg one : num poles", 0, poles.length );

		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg one : gain", 1, c0, delta );
	}

	@Test
	public void test2()
	{
		double[] poles = BSplineDecomposition.poles( 2 );
		Assert.assertEquals("deg two : num poles", 1, poles.length );

		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg two : gain", 8, c0, delta );
	}
	
	@Test
	public void test3()
	{
		double[] poles = BSplineDecomposition.poles( 3 );
		Assert.assertEquals("deg three : num poles", 1, poles.length );
		
		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg three : gain", 6, c0, delta );
	}

	@Test
	public void test4()
	{
		double[] poles = BSplineDecomposition.poles( 4 );
		Assert.assertEquals("deg four : num poles", 2, poles.length );
		
		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg four : gain", 384, c0, delta );
	}

	@Test
	public void test5()
	{
		double[] poles = BSplineDecomposition.poles( 5 );
		Assert.assertEquals("deg five : num poles", 2, poles.length );
		
		double c0  = BSplineDecomposition.gainFromPoles( poles );
		Assert.assertEquals("deg five : gain", 120, c0, delta );
	}

}
