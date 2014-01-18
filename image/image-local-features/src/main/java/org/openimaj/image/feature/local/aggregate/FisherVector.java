package org.openimaj.image.feature.local.aggregate;

import java.util.List;

import org.openimaj.citation.annotation.Reference;
import org.openimaj.citation.annotation.ReferenceType;
import org.openimaj.citation.annotation.References;
import org.openimaj.feature.ArrayFeatureVector;
import org.openimaj.feature.FloatFV;
import org.openimaj.feature.local.LocalFeature;
import org.openimaj.math.statistics.distribution.MixtureOfGaussians;
import org.openimaj.ml.gmm.GaussianMixtureModelEM;
import org.openimaj.ml.gmm.GaussianMixtureModelEM.CovarianceType;

/**
 * Implementation of the Fisher Vector (FV) encoding scheme. FV provides a way
 * of encoding a set of vectors (e.g. local features) as a single vector that
 * encapsulates the first and second order residuals of the vectors from a
 * gaussian mixture model.
 * <p>
 * The dimensionality of the output vector is 2*K*D where K is the number of
 * Gaussians in the mixture, and D is the descriptor dimensionality. Note that
 * only the diagonal values of the gaussian covariance matrices are used, and
 * thus you probably want to learn a {@link CovarianceType#Diagonal} or
 * {@link CovarianceType#Spherical} type gaussian with the
 * {@link GaussianMixtureModelEM} class.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 * 
 * @param <T>
 *            Primitive a type of the {@link ArrayFeatureVector}s used by the
 *            {@link LocalFeature}s that will be processed.
 */
@References(
		references = { @Reference(
				type = ReferenceType.Inproceedings,
				author = { "Perronnin, F.", "Dance, C." },
				title = "Fisher Kernels on Visual Vocabularies for Image Categorization",
				year = "2007",
				booktitle = "Computer Vision and Pattern Recognition, 2007. CVPR '07. IEEE Conference on",
				pages = { "1", "8" },
				customData = {
						"keywords", "Gaussian processes;gradient methods;image classification;Fisher kernels;Gaussian mixture model;generative probability model;gradient vector;image categorization;pattern classification;visual vocabularies;Character generation;Feeds;Image databases;Kernel;Pattern classification;Power generation;Signal generators;Spatial databases;Visual databases;Vocabulary",
						"doi", "10.1109/CVPR.2007.383266",
						"ISSN", "1063-6919"
				}
				),
				@Reference(
						type = ReferenceType.Inproceedings,
						author = { "Perronnin, Florent", "S\'{a}nchez, Jorge", "Mensink, Thomas" },
						title = "Improving the Fisher Kernel for Large-scale Image Classification",
						year = "2010",
						booktitle = "Proceedings of the 11th European Conference on Computer Vision: Part IV",
						pages = { "143", "", "156" },
						url = "http://dl.acm.org/citation.cfm?id=1888089.1888101",
						publisher = "Springer-Verlag",
						series = "ECCV'10",
						customData = {
								"isbn", "3-642-15560-X, 978-3-642-15560-4",
								"location", "Heraklion, Crete, Greece",
								"numpages", "14",
								"acmid", "1888101",
								"address", "Berlin, Heidelberg"
						}
				)
		})
public class FisherVector<T> implements VectorAggregator<ArrayFeatureVector<T>, FloatFV> {
	private MixtureOfGaussians gmm;
	private boolean hellinger;
	private boolean l2normalise;

	/**
	 * Construct with the given mixture of Gaussians and optional improvement
	 * steps. The covariance matrices of the gaussians are all assumed to be
	 * diagonal, and will be treated as such; any non-zero off-diagonal values
	 * will be completely ignored.
	 * 
	 * @param gmm
	 *            the mixture of gaussians
	 * @param hellinger
	 *            if true then use Hellinger's kernel rather than the linear one
	 *            by signed square rooting the values in the final vector
	 * @param l2normalise
	 *            if true then apply l2 normalisation to the final vector. This
	 *            occurs after the Hellinger step if it is used.
	 */
	public FisherVector(MixtureOfGaussians gmm, boolean hellinger, boolean l2normalise) {
		this.gmm = gmm;
		this.hellinger = hellinger;
		this.l2normalise = l2normalise;
	}

	/**
	 * Construct the standard Fisher Vector encoder with the given mixture of
	 * Gaussians. The covariance matrices of the gaussians are all assumed to be
	 * diagonal, and will be treated as such; any non-zero off-diagonal values
	 * will be completely ignored.
	 * 
	 * @param gmm
	 *            the mixture of gaussians
	 */
	public FisherVector(MixtureOfGaussians gmm) {
		this(gmm, false);
	}

	/**
	 * Construct the Fisher Vector encoder with the given mixture of Gaussians
	 * and the optional improvement steps (in the sense of the VLFeat
	 * documentation). The covariance matrices of the gaussians are all assumed
	 * to be diagonal, and will be treated as such; any non-zero off-diagonal
	 * values will be completely ignored. For the improved version, the final
	 * vector is projected into Hellinger's kernel and then l2 normalised.
	 * 
	 * @param gmm
	 *            the mixture of gaussians
	 * @param improved
	 *            if true then Hellinger's kernel is used, and the vector is l2
	 *            normalised.
	 */
	public FisherVector(MixtureOfGaussians gmm, boolean improved) {
		this(gmm, improved, improved);
	}

	@Override
	public FloatFV aggregate(List<? extends LocalFeature<?, ? extends ArrayFeatureVector<T>>> features) {
		if (features == null || features.size() <= 0)
			return null;

		final int K = this.gmm.gaussians.length;
		final int D = features.get(0).getFeatureVector().length();

		final float[] vector = new float[2 * K * D];

		for (final LocalFeature<?, ? extends ArrayFeatureVector<T>> f : features) {
			final double[] x = f.getFeatureVector().asDoubleVector();
			final double[] logPost = gmm.predictLogPosterior(x);

			for (int k = 0; k < K; k++) {
				final double posterior = Math.exp(logPost[k]);
				final double[] mean = gmm.gaussians[k].getMean().getArray()[0];

				for (int j = 0; j < D; j++) {
					final double var = gmm.gaussians[k].getCovariance(j, j);
					final double diff = (x[j] - mean[j]) / var;

					vector[k * 2 + j * D] += posterior * diff;
					vector[k * 2 + 1 + j * D] += posterior * ((diff * diff) - 1);
				}
			}
		}

		for (int k = 0; k < K; k++) {
			final double wt1 = 1.0 / (features.size() * Math.sqrt(gmm.weights[k]));
			final double wt2 = 1.0 / (features.size() * Math.sqrt(2 * gmm.weights[k]));

			for (int j = 0; j < D; j++) {
				vector[k * 2 + j * D] *= wt1;
				vector[k * 2 + 1 + j * D] *= wt2;
			}
		}

		final FloatFV out = new FloatFV(vector);

		if (hellinger) {
			for (int i = 0; i < out.values.length; i++) {
				out.values[i] = (float) (out.values[i] > 0 ? Math.sqrt(out.values[i]) :
						-1 * Math.sqrt(-1 * out.values[i]));
			}
		}

		if (l2normalise) {
			// l2 norm
			double sumsq = 0;
			for (int i = 0; i < out.values.length; i++) {
				sumsq += (out.values[i] * out.values[i]);
			}
			final float norm = (float) (1.0 / Math.sqrt(sumsq));
			for (int i = 0; i < out.values.length; i++) {
				out.values[i] *= norm;
			}
		}

		return out;
	}
}