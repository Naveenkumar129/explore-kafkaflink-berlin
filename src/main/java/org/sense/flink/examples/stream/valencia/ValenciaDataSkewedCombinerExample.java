package org.sense.flink.examples.stream.valencia;

import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_COMBINER;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_DISTRICT_KEY_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_DISTRICT_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_SINK;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_SOURCE;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_STRING_MAP;
import static org.sense.flink.util.MetricLabels.METRIC_VALENCIA_WINDOW;

import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.operators.OneInputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.sense.flink.examples.stream.operator.impl.MapStreamBundleOperator;
import org.sense.flink.examples.stream.operator.impl.MapStreamBundleOperatorDynamic;
import org.sense.flink.examples.stream.trigger.impl.CountBundleTrigger;
import org.sense.flink.examples.stream.trigger.impl.CountBundleTriggerDynamic;
import org.sense.flink.examples.stream.udf.MapBundleFunction;
import org.sense.flink.examples.stream.udf.impl.MapBundleValenciaImpl;
import org.sense.flink.examples.stream.udf.impl.ValenciaDistrictItemTypeAggWindow;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemDistrictAsKeyMap;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemDistrictMap;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemKeySelector;
import org.sense.flink.examples.stream.udf.impl.ValenciaItemToStringMap;
import org.sense.flink.pojo.ValenciaItem;
import org.sense.flink.source.ValenciaItemConsumer;
import org.sense.flink.util.ValenciaItemType;

/**
 * 
 * @author Felipe Oliveira Gutierrez
 *
 */
public class ValenciaDataSkewedCombinerExample {

	private final String topic = "topic-valencia-data-skewed";

	public static void main(String[] args) throws Exception {
		new ValenciaDataSkewedCombinerExample("127.0.0.1", "127.0.0.1");
	}

	public ValenciaDataSkewedCombinerExample(String ipAddressSource01, String ipAddressSink) throws Exception {
		disclaimer();
		boolean dynamicCombiner = true;
		boolean offlineData = true;
		boolean collectWithTimestamp = true;
		boolean skewedDataInjection = true;

		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

		// @formatter:off
		// Sources -> add synthetic data -> map latitude and longitude to districts in Valencia -> extract the key(district)
		DataStream<Tuple2<Long , ValenciaItem>> streamTrafficJam = env
				.addSource(new ValenciaItemConsumer(ValenciaItemType.TRAFFIC_JAM, Time.seconds(20).toMilliseconds(), collectWithTimestamp, offlineData, skewedDataInjection)).name(METRIC_VALENCIA_SOURCE + "-" + ValenciaItemType.TRAFFIC_JAM)
				.map(new ValenciaItemDistrictMap()).name(METRIC_VALENCIA_DISTRICT_MAP)
				.map(new ValenciaItemDistrictAsKeyMap()).name(METRIC_VALENCIA_DISTRICT_KEY_MAP)
				;
		DataStream<Tuple2<Long , ValenciaItem>> streamAirPollution = env
				.addSource(new ValenciaItemConsumer(ValenciaItemType.AIR_POLLUTION, Time.seconds(20).toMilliseconds(), collectWithTimestamp, offlineData, skewedDataInjection)).name(METRIC_VALENCIA_SOURCE + "-" + ValenciaItemType.AIR_POLLUTION)
				.map(new ValenciaItemDistrictMap()).name(METRIC_VALENCIA_DISTRICT_MAP)
				.map(new ValenciaItemDistrictAsKeyMap()).name(METRIC_VALENCIA_DISTRICT_KEY_MAP)
				;

		// Union -> Combiner(dynamic or static) -> Average -> Print
		streamTrafficJam.union(streamAirPollution)
				.transform(METRIC_VALENCIA_COMBINER, TypeInformation.of(new TypeHint<Tuple2<Long, ValenciaItem>>(){}), getCombinerOperator(dynamicCombiner)).name(METRIC_VALENCIA_COMBINER)
				.keyBy(new ValenciaItemKeySelector())
				.window(TumblingProcessingTimeWindows.of(Time.seconds(30)))
				.apply(new ValenciaDistrictItemTypeAggWindow()).name(METRIC_VALENCIA_WINDOW)
				.map(new ValenciaItemToStringMap()).name(METRIC_VALENCIA_STRING_MAP)
				// .addSink(new MqttStringPublisher(ipAddressSink, topic)).name(METRIC_VALENCIA_SINK)
				.print().name(METRIC_VALENCIA_SINK)
				;

		env.execute(ValenciaDataSkewedCombinerExample.class.getSimpleName());
		// @formatter:on
	}

	private OneInputStreamOperator<Tuple2<Long, ValenciaItem>, Tuple2<Long, ValenciaItem>> getCombinerOperator(
			boolean dynamicCombiner) throws Exception {
		// @formatter:off
		MapBundleFunction<Long, ValenciaItem, Tuple2<Long, ValenciaItem>, Tuple2<Long, ValenciaItem>> myMapBundleFunction = new MapBundleValenciaImpl();
		KeySelector<Tuple2<Long, ValenciaItem>, Long> keyBundleSelector = (KeySelector<Tuple2<Long, ValenciaItem>, Long>) value -> value.f0;

		if (dynamicCombiner) {
			CountBundleTriggerDynamic<Long, Tuple2<Long, ValenciaItem>> bundleTrigger = new CountBundleTriggerDynamic<Long, Tuple2<Long, ValenciaItem>>();
			return new MapStreamBundleOperatorDynamic<Long, ValenciaItem, Tuple2<Long, ValenciaItem>, Tuple2<Long, ValenciaItem>>(myMapBundleFunction, bundleTrigger, keyBundleSelector);
		} else {
			long tupleAmountToCombine = 10;
			CountBundleTrigger<Tuple2<Long, ValenciaItem>> bundleTrigger = new CountBundleTrigger<Tuple2<Long, ValenciaItem>>(tupleAmountToCombine);
			return new MapStreamBundleOperator<>(myMapBundleFunction, bundleTrigger, keyBundleSelector);
		}
		// @formatter:on
	}

	private void disclaimer() {
		System.out.println("Disclaimer...");
	}
}
