package org.janelia.saalfeldlab.bspline;

import java.util.Arrays;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.bspline.BSplineDecomposition;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class BSplineBenchmarks {

	public static void main(String[] args)
	{
		RandomAccessibleInterval<DoubleType> flyImg = LazyBSpline.flyImg();

		benchmarkPaddingOptimization( flyImg, new int[]{ 64, 64, 64 }, false );

		System.out.println("done");
	}

	/**
	 * Benchmark use 
	 * 
	 * @param img the image
	 * @param blockSize the block size
	 */
	public static void benchmarkPaddingOptimization( 
			final RandomAccessibleInterval<DoubleType> img,
			final int[] blockSize,
			final boolean doOptimization )
	{
		ExtendedRandomAccessibleInterval<DoubleType, RandomAccessibleInterval<DoubleType>> ext = Views.extendMirrorSingle( img );

		final BSplineDecomposition<DoubleType,DoubleType> coefsAlg = new BSplineDecomposition<DoubleType,DoubleType>( 3, ext );
		coefsAlg.setDoOptimization( doOptimization );
		coefsAlg.setDebug( true );
	
		final long[] dimensions = Intervals.dimensionsAsLongArray( img );
		final CellGrid grid = new CellGrid(dimensions, blockSize);
		
		ArrayImg<DoubleType, DoubleArray> cell = ArrayImgs.doubles( Arrays.stream(blockSize).mapToLong( x -> x ).toArray() );
		
		long[] min = new long[ img.numDimensions() ];
		int[] dim = new int[ img.numDimensions() ];

		long total = 0;

		long i = 0;
		IntervalIterator it = new IntervalIterator( grid.getGridDimensions() );
		while( it.hasNext())
		{
			it.fwd();
			grid.getCellDimensions( i, min, dim );

			IntervalView<DoubleType> cellTranslated = Views.translate( cell, min );
			long startTime = System.currentTimeMillis();

			coefsAlg.accept( cellTranslated );

			long endTime = System.currentTimeMillis();
			System.out.println( " " + (endTime - startTime) +" ms" );

			total += (endTime - startTime );

			i++;
		}

		System.out.println( "total block processing time: " + total );
	}
	

}
