package com.ccc.sendalyzeit.textanalytics.algorithms.deeplearning.sda.matrix.jblas.iterativereduce.actor;

import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ccc.sendalyzeit.deeplearning.berkeley.Pair;
import com.ccc.sendalyzeit.textanalytics.algorithms.deeplearning.nn.matrix.jblas.BaseMultiLayerNetwork;
import com.ccc.sendalyzeit.textanalytics.ml.scaleout.conf.Conf;
import com.ccc.sendalyzeit.textanalytics.ml.scaleout.conf.DeepLearningConfigurable;
import com.ccc.sendalyzeit.textanalytics.ml.scaleout.iterativereduce.ComputableWorker;
import com.ccc.sendalyzeit.textanalytics.ml.scaleout.iterativereduce.jblas.UpdateableMatrix;

import akka.actor.UntypedActor;
import akka.japi.Creator;

public class WorkerActor extends UntypedActor implements DeepLearningConfigurable,ComputableWorker<UpdateableMatrix> {
	private BaseMultiLayerNetwork network;
	private DoubleMatrix combinedInput;
	
	protected UpdateableMatrix workerMatrix;
	private static Logger log = LoggerFactory.getLogger(WorkerActor.class);
	int fineTuneEpochs;
	int preTrainEpochs;
	int[] hiddenLayerSizes;
	int numOuts;
	int numIns;
	int numHiddenNeurons;
	long seed;
	double learningRate;
	double corruptionLevel;
	private DoubleMatrix outcomes;
	Object[] extraParams;
	
	public WorkerActor(Conf conf) {
		setup(conf);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Object message) throws Exception {
		if(message instanceof List) {
			List<Pair<DoubleMatrix,DoubleMatrix>> input = (List<Pair<DoubleMatrix,DoubleMatrix>>) message;
			updateTraining(input);
			
		}
		else if(message instanceof UpdateMessage) {
			UpdateMessage m = (UpdateMessage) message;
			workerMatrix = m.getUpdateable();
		}
		else
			unhandled(message);
	}

	private void updateTraining(List<Pair<DoubleMatrix,DoubleMatrix>> list) {
		DoubleMatrix newInput = new DoubleMatrix(list.size(),list.get(0).getFirst().columns);
		DoubleMatrix newOutput = new DoubleMatrix(list.size(),list.get(0).getSecond().columns);
		for(int i = 0; i < list.size(); i++) {
			newInput.putRow(i,list.get(i).getFirst());
			newOutput.putRow(i,list.get(i).getSecond());
		}
		this.combinedInput = newInput;
		this.outcomes = newOutput;
		UpdateableMatrix work = compute();
		log.info("Updating parent actor...");
		context().parent().tell(work, getSelf());
	}
	
	@Override
	public UpdateableMatrix compute(List<UpdateableMatrix> records) {
		return compute();
	}

	@Override
	public UpdateableMatrix compute() {
		network.trainNetwork(combinedInput, outcomes,extraParams);
		return new UpdateableMatrix(network);
	}

	@Override
	public boolean incrementIteration() {
		return false;
	}
	
	@Override
	public void setup(Conf conf) {
		hiddenLayerSizes = conf.getIntsWithSeparator(LAYER_SIZES, ",");
		numOuts = conf.getInt(OUT);
		numIns = conf.getInt(N_IN);
		numHiddenNeurons = conf.getIntsWithSeparator(LAYER_SIZES, ",").length;
		seed = conf.getLong(SEED);
		RandomGenerator rng = new MersenneTwister(conf.getLong(SEED));
		network = new BaseMultiLayerNetwork.Builder<>()
				.numberOfInputs(numIns).numberOfOutPuts(numOuts)
				.hiddenLayerSizes(hiddenLayerSizes).withRng(rng)
				.withClazz(conf.getClazz(CLASS)).build();
		learningRate = conf.getDouble(LEARNING_RATE);
		preTrainEpochs = conf.getInt(PRE_TRAIN_EPOCHS);
		fineTuneEpochs = conf.getInt(FINE_TUNE_EPOCHS);
		corruptionLevel = conf.getDouble(CORRUPTION_LEVEL);
		extraParams = conf.loadParams(PARAMS);
	}

	

	@Override
	public UpdateableMatrix getResults() {
		return workerMatrix;
	}

	@Override
	public void update(UpdateableMatrix t) {
		this.workerMatrix = t;
	}

	
	public static class WorkerActorFactory implements Creator<WorkerActor> {

		/**
		 * 
		 */
		private static final long serialVersionUID = 381253681712601968L;

		public WorkerActorFactory(Conf conf) {
			this.conf = conf;
		}
		
		private Conf conf;
		
		@Override
		public WorkerActor create() throws Exception {
			return new WorkerActor(conf);
		}
		
	}
	
	
}
