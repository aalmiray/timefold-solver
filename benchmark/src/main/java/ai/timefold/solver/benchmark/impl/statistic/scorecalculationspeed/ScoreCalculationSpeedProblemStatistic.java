package ai.timefold.solver.benchmark.impl.statistic.scorecalculationspeed;

import java.awt.BasicStroke;
import java.io.File;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.timefold.solver.benchmark.config.statistic.ProblemStatisticType;
import ai.timefold.solver.benchmark.impl.report.BenchmarkReport;
import ai.timefold.solver.benchmark.impl.result.ProblemBenchmarkResult;
import ai.timefold.solver.benchmark.impl.result.SingleBenchmarkResult;
import ai.timefold.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import ai.timefold.solver.benchmark.impl.statistic.ProblemStatistic;
import ai.timefold.solver.benchmark.impl.statistic.SubSingleStatistic;
import ai.timefold.solver.benchmark.impl.statistic.common.MillisecondsSpentNumberFormat;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class ScoreCalculationSpeedProblemStatistic extends ProblemStatistic {

    protected File graphFile = null;

    public ScoreCalculationSpeedProblemStatistic(ProblemBenchmarkResult problemBenchmarkResult) {
        super(problemBenchmarkResult, ProblemStatisticType.SCORE_CALCULATION_SPEED);
    }

    @Override
    public SubSingleStatistic createSubSingleStatistic(SubSingleBenchmarkResult subSingleBenchmarkResult) {
        return new ScoreCalculationSpeedSubSingleStatistic(subSingleBenchmarkResult);
    }

    /**
     * @return never null
     */
    @Override
    public List<File> getGraphFileList() {
        return Collections.singletonList(graphFile);
    }

    // ************************************************************************
    // Write methods
    // ************************************************************************

    @Override
    public void writeGraphFiles(BenchmarkReport benchmarkReport) {
        Locale locale = benchmarkReport.getLocale();
        NumberAxis xAxis = new NumberAxis("Time spent");
        xAxis.setNumberFormatOverride(new MillisecondsSpentNumberFormat(locale));
        NumberAxis yAxis = new NumberAxis("Score calculation speed per second");
        yAxis.setNumberFormatOverride(NumberFormat.getInstance(locale));
        yAxis.setAutoRangeIncludesZero(false);
        XYPlot plot = new XYPlot(null, xAxis, yAxis, null);
        plot.setOrientation(PlotOrientation.VERTICAL);
        int seriesIndex = 0;
        for (SingleBenchmarkResult singleBenchmarkResult : problemBenchmarkResult.getSingleBenchmarkResultList()) {
            XYSeries series = new XYSeries(singleBenchmarkResult.getSolverBenchmarkResult().getNameWithFavoriteSuffix());
            XYItemRenderer renderer = new XYLineAndShapeRenderer();
            if (singleBenchmarkResult.hasAllSuccess()) {
                ScoreCalculationSpeedSubSingleStatistic subSingleStatistic =
                        (ScoreCalculationSpeedSubSingleStatistic) singleBenchmarkResult
                                .getSubSingleStatistic(problemStatisticType);
                List<ScoreCalculationSpeedStatisticPoint> points = subSingleStatistic.getPointList();
                for (ScoreCalculationSpeedStatisticPoint point : points) {
                    long timeMillisSpent = point.getTimeMillisSpent();
                    long scoreCalculationSpeed = point.getScoreCalculationSpeed();
                    series.add(timeMillisSpent, scoreCalculationSpeed);
                }
            }
            plot.setDataset(seriesIndex, new XYSeriesCollection(series));

            if (singleBenchmarkResult.getSolverBenchmarkResult().isFavorite()) {
                // Make the favorite more obvious
                renderer.setSeriesStroke(0, new BasicStroke(2.0f));
            }
            plot.setRenderer(seriesIndex, renderer);
            seriesIndex++;
        }
        JFreeChart chart = new JFreeChart(problemBenchmarkResult.getName() + " score calculation speed statistic",
                JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        graphFile = writeChartToImageFile(chart, problemBenchmarkResult.getName() + "ScoreCalculationSpeedStatistic");
    }

}
