# Improving the outfit recommender

Research date: 17 November 2026. Based on the current working tree and the
[implementation plan](2026-11-12-IMPLEMENTATION_PLAN.md). This is a research
proposal, not an implemented or benchmarked model change. It assumes learning
remains local to the user and works across Android, iOS, desktop and Web.

The recommended direction is a small Bayesian comfort model with nonlinear
weather–outfit behaviour, incremental updates and uncertainty estimates. Keep
the existing warmth constants initially as prior information. Use the existing
cold/perfect/warm feedback to learn thermal suitability alongside the 1–5
feeling score. Evaluate active learning after the prediction and data pipelines
are reliable.

Changing the learning method alone will not address the current limitations.
Feature representation, the feedback target, candidate generation and weather
alignment all matter. The recommendations below are engineering judgements for
this app; the cited studies do not establish their accuracy on its users.

## Current functionality

| Stage | Current implementation |
| --- | --- |
| Clothing features | `WarmthEncoding.kt` multiplies fixed material and type weights, sums warmth by body zone, counts upper layers and produces eight features including bias, temperature and rain. |
| Feedback | Each survey stores an overall 1–5 feeling score and a cold/perfect/warm verdict. |
| Training | `OutfitLogRepository.retrain()` fits the feeling score on surveyed entries with usable temperature. It prefers the snapshot min/max mean, then current temperature. |
| Model | `LinearRegression.kt` solves ridge regression with an unpenalised intercept. Other coefficients receive a penalty of `lambda * sampleCount`. |
| Updating | Survey submission/edit and entry deletion schedule an asynchronous full refit. App startup checks for a missing model or a newer survey. |
| Prediction | `Scoring.modelScore()` calculates a dot product and clamps the result to 1–5. |
| Modes | With temperature available: zero surveys uses fallback; at least one uses heuristics; ten or more plus a stored model enables model mode. Missing temperature uses fallback. |
| Candidates | Logged outfits plus bounded wardrobe combinations, subject to archive/laundry rules; the engine returns up to three results. |

Relevant sources: [feature encoding](../shared/src/data/recommendation/WarmthEncoding.kt),
[training](../shared/src/data/outfit/OutfitLogRepository.kt),
[regression](../shared/src/data/recommendation/LinearRegression.kt),
[scoring](../shared/src/data/recommendation/Scoring.kt),
[engine](../shared/src/data/recommendation/RecommendationEngine.kt).

## Findings that should precede a model replacement

**The current regression cannot learn weather-dependent changes in outfit
preference.** Its unclamped score has the form

```text
score(outfit, weather) = bias + a * temperature + b * rain
                        + clothingCoefficients · clothingFeatures(outfit)
```

For two outfits scored in the same weather, temperature and rain cancel when
their scores are subtracted. The learned ordering therefore cannot reverse as
the weather changes. Clamping can create ties, but cannot supply the missing
relationship. Candidate filtering, the heuristic and the separate historical
temperature penalty can change the final ordering; that behaviour is outside
the learned score. This follows directly from
[WarmthEncoding](../shared/src/data/recommendation/WarmthEncoding.kt) and
[Scoring](../shared/src/data/recommendation/Scoring.kt).

An interaction such as `temperature * upperWarmth` lets the benefit of upper
warmth vary with temperature. A squared warmth term can represent an optimum
between too little and too much insulation. A temperature-squared term alone
still adds the same quantity to every candidate and does not fix ranking.

Other issues found by source inspection:

| Finding | Consequence and proposed correction |
| --- | --- |
| Candidates are computed using `today`, while `selectedDate` lives inside `RecommendationContent`. | Selecting a calendar day changes the weather note but not the candidate input. Feed the selected date into the engine calculation. See [RecommendationScreen.kt](../shared/src/ui/recommendation/RecommendationScreen.kt). |
| Forecast parsing takes every eighth three-hour record and copies that record's `temp_min`/`temp_max`. | These are not daily extrema. Group all relevant forecast intervals by local date, or by the user's intended wearing period. See [WeatherRepository.kt](../shared/src/data/weather/WeatherRepository.kt). |
| Model activation counts all surveyed entries, rather than usable training samples. | Ten surveys can activate a model trained on fewer weather-labelled examples. Initially correct the count; ultimately use coverage and uncertainty as well. See [OutfitDao.kt](../shared/src/data/outfit/OutfitDao.kt). |
| `(logged + constructed).take(60)` happens before scoring. | Sixty eligible logged outfits exclude every constructed outfit, regardless of its potential score. Budget the sources separately and retain candidates by suitability rather than input order. |
| Constructed combinations contain one upper plus one lower, or one full-body item. | They cannot construct a base layer plus an additional outer layer, despite the plan. A better scorer cannot recover an outfit never generated. |
| Laundry replacement minimises distance from the original fixed warmth. | A replacement is not selected using the new outfit's predicted comfort. Re-score a small set of structurally valid replacements. |
| Deduplication includes the logged entry ID alongside the piece set. | Identical clothing combinations from different entries can occupy multiple top-three positions. Deduplicate piece-based results by their actual pieces; preserve a suitable identity for selfie-only results. |
| Outfit edits do not schedule retraining, and several retrains can run concurrently. | Changes to training inputs can leave stale coefficients; older computations can publish after newer ones. Introduce one application-level training coordinator and a data revision. |
| Historical piece relations resolve to current clothing metadata. | Editing material/type changes historical training features. Decide whether that edit corrects history, and rebuild accordingly; version or snapshot relevant inputs for reproducibility. |
| A selfie-only entry is encoded as zero clothing warmth and layers. | Unknown clothing is treated as observed absence. Use a missing-clothing indicator or a separate history-only path; exclude it from garment-attribution updates. |

Candidate findings are in
[CandidateGenerator.kt](../shared/src/data/recommendation/CandidateGenerator.kt)
and [RecommendationEngine.kt](../shared/src/data/recommendation/RecommendationEngine.kt).
Training lifecycle and historical relations are in
[OutfitLogRepository.kt](../shared/src/data/outfit/OutfitLogRepository.kt)
and [OutfitModels.kt](../shared/src/data/outfit/OutfitModels.kt).

OpenWeather documents this endpoint as a three-hour forecast. Its per-record
min/max fields describe the moment of calculation; it also exposes wind,
humidity, feels-like temperature, precipitation probability and rain volume.
Those fields can improve the inputs without changing providers.
[OpenWeather forecast documentation](https://openweathermap.org/api/forecast5).

## What the research supports

Personal thermal comfort research supports learning individual preferences, but
does not establish a universal winning model. Kim and colleagues compared six
methods; random forests, kernel SVMs and regularised logistic regression had
similar aggregate performance in their office study. Learning curves also
varied substantially between individuals. This supports benchmarking compact
models and avoiding a universal ten-survey readiness rule.
[Kim et al., 2018](https://escholarship.org/content/qt54n6b7m3/qt54n6b7m3.pdf).

Awalgaonkar and colleagues used a Gaussian process with a preferred-temperature
peak and actively selected thermal queries. The relevant idea is to learn the
comfortable region using directional feedback. Their implementation uses HMC
sampling and focuses on indoor temperature; its results do not directly cover
outdoor clothing combinations, wetness or this app's runtime constraints.
[Awalgaonkar et al., 2019](https://arxiv.org/abs/1903.09094).

Active learning has also been evaluated with real thermal-comfort feedback.
Tekler and colleagues reported up to 46% less labelling effort for thermal
comfort in a study involving 58 participants and 2,727 responses. This motivates
testing selective questions here, but is not an expected improvement estimate
for a personal wardrobe with a handful of surveys.
[Tekler et al., 2023](https://doi.org/10.1016/j.buildenv.2023.110148).

## Model choices for this app

The suitability column is a proposal based on the current architecture and
expected sparse personal data, not a measured comparison. Let `n` be training
examples and `d` be model parameters/features.

| Approach | Updating and trade-offs | Suggested role |
| --- | --- | --- |
| Ridge with interactions and a few nonlinear features | Same small solver; full fitting is roughly `O(n*d² + d³)`. Needs feature scaling and regularisation. No uncertainty estimate in the current implementation. | First baseline: isolate the gain from a better representation. |
| Online Bayesian regression / ordinal comfort model | Stores parameter estimates and uncertainty. Scalar Gaussian updates are `O(d²)`; bounded approximate ordinal updates can also use rank-one `O(d²)` work. Priors help when observations are scarce. | Preferred direction for local personalisation. |
| Similar-outfit / nearest-neighbour model | Almost no fitting; query cost grows with stored history. Weak support for new garments and weather outside past experience. | Useful inexpensive benchmark and explanatory fallback. |
| Small Gaussian process | Flexible nonlinear predictions and uncertainty. Dense Gaussian regression has `O(n³)` fitting and `O(n²)` storage; ordinal likelihoods and hyperparameter optimisation add work. | Strong experimental challenger for a small history, with a bounded data budget. |
| Small tree ensemble | Captures nonlinear interactions without specifying every product term. Retraining may be practical for small data, but training integration across all Kotlin targets requires additional work. | Benchmark before choosing it over a compact Bayesian model. |

The Bayesian update relationship is described in
[Särkkä, chapter 3](https://users.aalto.fi/~ssarkka/pub/cup_book_online_20131111.pdf).
Ordinal threshold likelihoods and approximate inference are described by
[Chu and Ghahramani](https://www.jmlr.org/beta/papers/v6/chu05a.html).
Gaussian process scaling is discussed by
[Cambridge's GP research group](https://mlg.eng.cam.ac.uk/research/gp/).
Tree ensembles are described in
[Breiman's random forests paper](https://www.stat.berkeley.edu/~breiman/randomforest2001.pdf).

A neural recommender would introduce more modelling and deployment work than I
would prioritise for the current feedback stream. There is also no shared
user–item interaction dataset here to justify collaborative filtering as the
first improvement.

## Recommended prediction design

Retain both survey answers. They answer different questions:

- The 1–5 rating measures overall feeling and is the current specified target.
- Cold/perfect/warm provides a direction for thermal correction. A low overall
  rating alone does not say whether to add or remove warmth.

Start by improving the existing rating model with a small, scaled feature set:
zone warmth, layers, temperature–warmth interactions and a limited quadratic
warmth term. Consider a heuristic prior plus a learned residual, so predictions
start from a sensible weather-dependent function. Avoid an unrestricted
polynomial with many interactions fitted to ten examples.

Then add a Bayesian ordinal thermal model. One possible latent model is:

```text
thermalSensation = personalOffset + weatherEffects
                   + zoneAndLayerEffects
                   + sum(garmentAdjustments)
                   + rain × protectionEffects
                   + wind × protectionEffects
```

Thresholds map this latent value to cold, comfortable or warm. Predict all three
probabilities rather than only an average class value. For example, as weather
gets warmer, the same coat can move through cold → comfortable → warm; its
probability of being comfortable rises and then falls. Even a compact latent
model can therefore produce weather-dependent preferences.

Use an ordinal probit/logistic likelihood, with an online approximation such as
assumed-density filtering or an online Laplace update. Treat the latent scale,
noise and thresholds consistently: fix a scale and use constrained/ordered
thresholds rather than estimating an unconstrained collection of interchangeable
parameters from sparse data. The threshold construction follows the ordinal
modelling approach described by
[Chu and Ghahramani](https://www.jmlr.org/beta/papers/v6/chu05a.html); its use for
this clothing pipeline is a proposed adaptation.

Initially learn broad user and zone/type effects. Add garment-specific residuals
with strong shrinkage towards zero as evidence accumulates. This lets a new
garment inherit a useful prior without fitting it independently from scratch.
The wardrobe's existing material/type score can remain the initial prior, as
requested; it need not be treated as a physical measurement.

Keep overall satisfaction prediction as a separate output. A possible later
ranking policy is to prefer thermally suitable outfits, then distinguish them
using predicted overall feeling. That would change the plan's sole 1–5 ranking
objective and should be recorded as a product decision before implementation.
The initial interaction-feature baseline can retain the existing objective.

## Where Kalman filtering fits

Kalman filtering is a method for updating uncertain state from observations.
Active learning chooses which observation to request. They can work together.
The distinction between choosing labels and updating a model is also central to
[Settles' active learning survey](https://research.cs.wisc.edu/techreports/2009/TR1648.pdf).

For a continuous target, Gaussian coefficient prior and fixed feature map, the
Bayesian regression update is a Kalman measurement update. With feature vector
`x`, parameter mean `m`, covariance `P` and observation-noise variance `R`:

```text
K     = P*x / (R + xᵀ*P*x)
m_new = m + K*(y - xᵀ*m)
P_new = P - K*xᵀ*P
```

This updates existing estimates after one survey without refitting the whole
history. A drift model can increase uncertainty between observations, for
example `P_prior = P + Q*elapsedTime`, allowing adaptation to changing personal
preferences. With no drift, matched Gaussian assumptions and a fixed prior,
recursive and batch Bayesian regression agree.
[Särkkä, sections 3.2–3.4](https://users.aalto.fi/~ssarkka/pub/cup_book_online_20131111.pdf).

For this app, I would initially allow drift mainly in the user's comfort offset,
with stable garment priors. Weather is an observed input; it should not be
mistaken for unexplained drift in every garment. The appropriate drift rate
needs validation.

The ordinal verdict is not a Gaussian temperature measurement. Encoding it as
`-1, 0, +1` and running the equations above is only a prototype approximation.
Use the ordinal likelihood for the proposed thermal model. Likewise, making a
regression Bayesian without changing its features or decision rule would retain
the present ranking limitation.

The current fitter's `lambda * n` penalty is also not automatically equivalent
to a fixed Bayesian prior. Choose the intended regularisation/prior explicitly
when comparing batch and online implementations.

## Learning garment warmth under rain and wind

Use a stable baseline plus condition-dependent effects. A conceptual model is:

```text
effective contribution = prior contribution + learned personal correction
                         + rainExposure × learned wet-condition effect
                         + windExposure × learned wind-condition effect
```

These are subjective comfort contributions, not measured insulation in clo.
Initially pool condition effects by garment role/type; learning a separate rain
parameter for every item would require much more varied evidence.

Rain forecasts, actual wetness and garment protection are different signals.
A user might be indoors or under an umbrella; a shell may protect several
underlying layers. A whole-outfit protection feature is therefore useful in
addition to per-item effects. One rainy experience should not permanently
decrease the estimate of a garment's dry warmth.

Experiments demonstrate why a single rain adjustment is limited: water-repellent
uniform constructions differed in water uptake and perceived cold/discomfort
under controlled rain and movement. Those results support modelling protection
and exposure, not importing a universal numerical rain multiplier.
[Kwon et al., 2021](https://snu.elsevierpure.com/en/publications/performance-evaluation-of-water-repellent-combat-uniforms-using-a/).

Possible additions, in order of low collection cost:

1. Preserve forecast wind, rain probability/amount and feels-like information,
   with timestamps and location. Use one consistent weather representation for
   training and inference; do not manually double-count wind chill if using
   feels-like temperature.
2. Add an optional rain-/wind-protection attribute for outer garments.
3. Where uncertainty is actionable, ask whether the wearer was mostly indoors,
   active, or actually got wet. Avoid making all of these mandatory every day.

There is a fundamental attribution limit: if a sweater and coat are always worn
together, whole-outfit feedback cannot reliably determine their separate
contributions. Priors and uncertainty express this limitation. Varied outfits
or a targeted comparison can provide the missing information; a Kalman filter
cannot create it.

## Active learning and recommendation exploration

Begin with selective questions about outfits the user actually wore. For
example, after an uncertain rainy-day outcome, wetness/exposure information may
be more valuable than another overall score. A comparison such as which of two
owned jackets feels warmer can help distinguish items, but is a different kind
of label and needs an appropriate likelihood.

Query value should combine reducible uncertainty, likely future use and user
effort. High uncertainty alone can repeatedly select unusual or inherently
noisy situations. Measure how much prediction quality improves per question.
These are proposed application choices within the active-query framework in
[Settles' survey](https://research.cs.wisc.edu/techreports/2009/TR1648.pdf).

For later exploration, retain reliable choices and optionally use one
alternative slot for a plausible, less-known outfit. Thompson sampling samples
model parameters from the posterior and selects according to that sample,
balancing current reward with information gathering.
[Russo et al., A Tutorial on Thompson Sampling](https://arxiv.org/abs/1707.02038).

Apply exploration within the normal availability, coverage and comfort bounds;
keep the displayed slate stable during a session. The user's selection remains
their choice. For this app's top-three display, a full policy is a slate/choice
problem rather than automatically the single-action bandit in a textbook.

Record the displayed candidates, context, policy/model version, randomisation
probabilities when available, and the outfit actually worn. Only that outfit
receives its reported outcome; ignored suggestions are not negative labels.
Historical self-selected outfits do not reveal outcomes for alternatives never
worn. Bandit evaluation requires appropriate logging and support assumptions,
as illustrated by
[Li et al., offline contextual-bandit evaluation](https://arxiv.org/abs/1003.5956).

## Keeping recommendations available

The current eight-feature fit is already small: approximately
`O(n*8² + 8³)` arithmetic plus data access. It is scheduled through
`Dispatchers.Default`, so there is no measured evidence here that fitting is the
existing bottleneck. However, candidate generation and scoring currently run
inside a composable's `remember` calculation. Faster fitting alone will not
resolve that UI work.

Recommended execution design:

1. Score using the latest complete model snapshot; use the prior/fallback when
   no model exists. Do not wait for a new training run to display suggestions.
2. Perform ranking and training outside composition. Decode coefficients once
   per model snapshot instead of once per candidate.
3. Use one application-level update coordinator, coalesce redundant requests,
   and publish only a complete result matching a captured training-data revision.
4. Store model version, feature schema/scaling, posterior parameters, data
   revision and usable sample information together.
5. Treat survey edits as replacements. For the small history, replay/rebuild
   after edits, deletions or changes to historical inputs; do not append the
   corrected survey as another independent observation. Dynamic models require
   chronological replay or a more careful correction scheme.
6. Keep browser work bounded. In coroutines 1.11.0, the JS/Wasm Main dispatcher
   delegates to Default; changing to Default does not by itself establish a
   separate worker. Use cooperative chunks or a Web Worker if profiling shows
   frame stalls. See the
   [official dispatcher implementation](https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/kotlinx-coroutines-core/jsAndWasmShared/src/Dispatchers.kt).

For illustration, a dense covariance with 32 parameters contains 1,024 doubles,
or 8 KiB of numeric storage. Per-garment parameters increase this dimension, and
runtime allocations/storage add overhead. This is an operation/storage estimate,
not a device latency measurement. Benchmark ranking and updates separately on
the weakest supported phone and in the browser.

## Suggested implementation and evaluation order

| Step | Scope | Evidence required |
| --- | --- | --- |
| 1 | Correct date/weather alignment, candidate generation, usable-sample counting and model invalidation. | Behaviour tests for calendar inputs, layers, candidate caps, identical outfit results and edit/delete handling. |
| 2 | Establish a scaled interaction/quadratic ridge baseline alongside the existing heuristic. | Weather-dependent preference reversal and chronological held-out rating accuracy. |
| 3 | Evaluate a compact Bayesian model and the ordinal thermal output; retain priors when evidence is weak. | Calibration, cold/warm errors, feeling-score error, update/ranking latency and reliable state persistence. |
| 4 | Add pooled condition effects and strongly regularised garment corrections. | Improvement on held-out garments/combinations and rainy/windy contexts, with uncertainty retained for unidentifiable items. |
| 5 | Trial targeted questions and constrained exploration. | Better actual wearer outcomes or fewer required questions, with correctly attributed feedback. |

Use a chronological replay: predict each available outfit observation using only
information available before its feedback, then update. Keep all revisions of an
entry from leaking across train/test boundaries. Compare against both the
current heuristic and an interaction-feature baseline; comparing only against
the present additive model would overstate the value of a more complex method.

Track 1–5 score error, ordinal log loss/Brier score and calibration, actual
cold/warm complaint rates, outfit diversity, and p50/p95 update and ranking
times. Report results separately for sparse histories, repeated versus new
outfits and weather outside previous experience. Historical prediction metrics
do not by themselves prove that an untried top-three recommendation is better.

Synthetic fixtures can establish intended behaviour and detect regressions.
They cannot establish real recommendation quality. A small prospective trial
with the old and proposed model predicting in parallel is the useful next
evidence step before changing the default ranking policy.
