package degtiarenko.gutsol.ml

import java.lang.Math.*
import java.util.*

val euclideanMetric = { x: DataItem, y: DataItem -> sqrt(x.coords.zip(y.coords, { a, b -> pow(a - b, 2.0) }).sum()) }
val manhattanMetric = { x: DataItem, y: DataItem -> x.coords.zip(y.coords, { a, b -> abs(a - b) }).sum() }
val thirdDegreeMetric = { x: DataItem, y: DataItem -> pow(x.coords.zip(y.coords, { a, b -> pow(a - b, 3.0) }).sum(), 1.0 / 3.0) }

val metrics = listOf(Pair(euclideanMetric, "euclid"), Pair(manhattanMetric, "manhattan"),
        Pair(thirdDegreeMetric, "3rd-degree"))

val baseTransform = { p: DataItem -> p }
val circleTransform = { p: DataItem ->
    DataItem(doubleArrayOf(p.coords[0], p.coords[1],
            sqrt(pow(p.coords[0], 2.0) + pow(p.coords[1], 2.0))), p.category)
}

val spaceTransforms = listOf(Pair(baseTransform, "base"), Pair(circleTransform, "circle"))

val uniformKernel = { x: Double -> if (abs(x) > 1.0) 0.0 else 1.0 }
val triangularKernel = { x: Double -> if (abs(x) > 1.0) 0.0 else 1.0 - abs(x) }
val parabolicKernel = { x: Double -> if (abs(x) > 1.0) 0.0 else 0.75 * (1 - x * x) }
val biweightKernel = { x: Double -> if (abs(x) > 1.0) 0.0 else 0.9375 * (1 - x * x) * (1 - x * x)}

val kernels = listOf(Pair(uniformKernel, "uniform"), Pair(triangularKernel, "triangular"),
                     Pair(parabolicKernel, "parabolic"), Pair(biweightKernel, "biweight"))


fun main(args: Array<String>) {
    val items = Thread.currentThread().contextClassLoader.getResource("chips.txt")
            .readText().split("\n", "\r")
            .filter { s -> !s.isEmpty() }
            .map { s -> s.split(",") }
            .map { l -> DataItem(l[0], l[1], l[2]) }
            .toList()
    Collections.shuffle(items)
    val configs = ConfigGroup(spaceTransforms, metrics, kernels,
            IntArray(sqrt(items.size.toDouble()).toInt(), { i -> i + 2 }))

    val bestPredictor: Pair<Predictor?, Double> = getBestPredictor(configs, items, spaceTransforms,
            { conf, trans, itemz -> getBestPredictorInSpace(conf, trans, itemz) })
    println("Best F1-score is " + bestPredictor.second + " with predictor: " + bestPredictor.first!!.name)
    val visualizer = Visualizer(items)
    visualizer.drawPlot(bestPredictor.first as Predictor, "out")
}

private fun <T> getBestPredictor(configs: ConfigGroup, items: List<DataItem>, paramVariants: List<T>,
                                 nextFun: (ConfigGroup, T, List<DataItem>) -> Pair<Predictor?, Double>): Pair<Predictor?, Double> {
    var bestPredictor: Predictor? = null
    var bestScore = 0.0
    for (paramVar in paramVariants) {
        CrossValidator(items).forEachTestSet { trainList, testList ->
            val predictor = nextFun(configs, paramVar, trainList)
            if (testWithPredictor(testList, predictor.first!!) > bestScore) {
                bestPredictor = predictor.first
                bestScore = testWithPredictor(testList, predictor.first!!)
            }
        }
    }
    return Pair(bestPredictor, bestScore)
}

fun getBestPredictorInSpace(configs: ConfigGroup, spaceTransform: Pair<(DataItem) -> DataItem, String>,
                            items: List<DataItem>): Pair<Predictor?, Double> {
    val bestPredictor = getBestPredictor(configs.fixSpace(spaceTransform.second), items, metrics,
            { conf, metric, itemz -> getBestPredictorWithMetric(conf, metric, itemz) })
    println("Best predictor in space " + spaceTransform.second + " is " + bestPredictor.first!!.name)
    return bestPredictor
}

fun getBestPredictorWithMetric(configs: ConfigGroup, metric: Pair<(DataItem, DataItem) -> Double, String>,
                               items: List<DataItem>): Pair<Predictor?, Double> {
    val bestPredictor = getBestPredictor(configs.fixMetric(metric.second), items, kernels,
            { conf, kernel, itemz -> getBestPredictorWithKernel(conf, kernel, itemz) })
//    println("Best predictor with metric " + metric.second + " is " + bestPredictor.first!!.name)
    return bestPredictor
}

fun getBestPredictorWithKernel(configs: ConfigGroup, kernel: Pair<(Double) -> Double, String>,
                               items: List<DataItem>): Pair<Predictor?, Double> {
    val bestPredictor = getBestPredictor(configs.fixKernel(kernel.second), items, configs.kIterable.toList(),
            { conf, k, itemz -> Pair(conf.fixK(k).getPredictors(itemz)[0], 0.0) })
//    println("Best predictor with kernel " + kernel.second + " is " + bestPredictor.first!!.name)
    return bestPredictor
}

fun testWithPredictor(testList: List<DataItem>, predictor: Predictor): Double {
    val answers = testList.map { item -> predictor.predict(item) }

    return computeF1Score(answers, testList)
}

fun computeF1Score(answers: List<Int>, testList: List<DataItem>): Double {
    val compared = answers.zip(testList)
    val positiveAnswers = compared.filter { (result) -> result == 1 }
    val tpAnswers = positiveAnswers.filter { (result, expected) -> result == expected.category }
    val fnAnswers = compared.filter { (result, expected) -> result == 0 && expected.category == 1 }

    val tpSize = tpAnswers.size.toDouble()
    val pSize = positiveAnswers.size.toDouble()
    val fnSize = fnAnswers.size.toDouble()
    val precision = tpSize / pSize
    val recall = tpSize / (tpSize + fnSize)

    return 2 * precision * recall / (precision + recall)
}