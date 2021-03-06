#' @author Marwin Zuefle, Andre Bauer


#' @description Forecasts a given univariate time series in a hybrid manner and based on time series decomposition
#'
#' @title Perform the Forecast
#' @param tvp The time value pair: either vector of raw values or n-by-2 matrix (raw values in second column), or time series
#' @param horizon The number of values that should be forecast
#' @param hist.covar The covariates of the history used to build the model (matrix, nrows has to equal length (nrow) of tvp)
#' @param future.covar The covariates of the future used for prediction (matrix, same columns as hist.covar, nrow has to equal horizon)
#' @param repsANN Optional parameter: The amount of repeats for ANN. 20 by default.
#' @param doAnomDet  Optional parameter: Boolean whether anomaly detection shall be used for clustering. TRUE by default
#' @param replace.zeros  Optional parameter: If TRUE, all zeros will be replaced by the mean of the non-zero neighbors. TRUE by default
#' @param use.indicators  Optional parameter: If TRUE, additional information (e.g. a flag wheter there is a high remainder) will be returned. TRUE by default
#' @param save_fc  Optional parameter: Boolean wheter the forecast shall be saved as csv. FALSE by default
#' @param csv.path Optional parameter: The path for the saved csv-file. The current workspace by default.
#' @param csv.name Optional parameter: The name of the saved csvfile. Telescope by default.
#' @param debug Optional parameter: If TRUE, debugging information will be displayed. FALSE by default
#' @return The forecast
#' @examples
#' telescope.forecast(AirPassengers, horizon=10)
#' @export
telescope.forecast <- function(tvp, horizon, 
                               hist.covar,
                               future.covar,
                               repsANN = 20,doAnomDet = TRUE, replace.zeros = TRUE, use.indicators = TRUE, save_fc = FALSE, csv.path = '', csv.name = "Telescope", debug = FALSE) {

  use.second.freq <- TRUE
  sig.dif.factor <- 0.5
  plot <- TRUE

  startTime <- Sys.time()

  # Convert timeseries and extract information
  tvp <- extract.info(tvp, use.second.freq)

  # Remove all Anomalies on the raw time series first
  if(tvp$frequency>10) {
    tvp$values <- removeAnomalies(tvp$values,frequency = tvp$frequency, replace.zeros = replace.zeros)
  }

  hist.length <- length(tvp$values)

  # get the minimum value to shift all observations to real positive values
  minValue <- min(tvp$values)
  if(minValue<=0) {
    tvp$values <- tvp$values + abs(minValue) + 1
  }

  # use log if there is a significant trend in the forecast (STL with log delivers a multiplicative decomposition)
  use.log <- testTrend(tvp = tvp$values, frequency = tvp$frequency)

  if(use.log) {
    tvp$values <- log(tvp$values)
    print("using log for stl and forecast")
  }

  tsTrain <- ts(tvp$values,frequency=tvp$frequency)

  stlTrain <- stl(tsTrain,s.window = "periodic",t.window=length(tsTrain)/2)

  stlTraintrend <- stlTrain$time.series[,2]
  stlTrainremainder <- stlTrain$time.series[,3]

  # check if the time series has a high remainder
  high_remainder <- has.highRemainder(tvp$values,stlTrainremainder,use.log, sig.dif.factor)
  if(high_remainder){
    print("-------------- ATTENTION: High remainder in STL --------------")
  }

  # Search for the next best frequency
  if(tvp$use.second.freq) {
    print("estimating second freq")
    freq2 <- calcFrequencyPeriodogram(timeValuePair = tvp$values, asInteger = TRUE, difFactor = 0.5,maxIters = 10,ithBest = tvp$lastIterfreq + 1, PGramTvp = tvp$pgram)$frequency

    # second stl decomposition
    if(freq2 < (length(stlTrainremainder)/2)) {
      stlRemainder <- stl(ts(stlTrainremainder,frequency=freq2),s.window="periodic",t.window=length(stlTrainremainder)/2)
      stlRemainderSeason <- stlRemainder$time.series[,1]
      stlRemainderTrend <- stlRemainder$time.series[,2]
      print("finished estimating second freq positive")
    } else {
      print("second freq too long")
      tvp$use.second.freq <- FALSE
    }
  }

  # Forecast season according to stl decomposition
  fcSeason <- forecast.season(tvp, stlTrain, horizon)


  total.length <- hist.length+horizon
  # Add second period to fist period
  if(tvp$use.second.freq) {
    fullper2 <- as.integer(total.length/freq2)
    rest2 <- total.length-(fullper2*freq2)
    fcSeason2 <- rep(stlRemainderSeason[1:freq2],fullper2)
    if(rest2>0){
      fcSeason2 <- c(fcSeason2,stlRemainderSeason[1:rest2])
    }

    fcSeason <- fcSeason + fcSeason2
  }

  # Forecast trend with ARIMA (without seasonal models)
    tsTrainTrend <- ts(stlTraintrend,frequency = tvp$frequency)


  # Check for trend model: linear or exp
  model <- fittingModels(stlTrain,frequency = tvp$frequency,difFactor = 1.5, debug = debug)

  if(model$risky_trend_model) {
    print("-------------- ATTENTION: risky trend estimation --------------")
  }


  # Forecast trend according to the underlying trend model
  fcTrend <- forecast.trend(model$trendmodel,tsTrainTrend, tvp$frequency, horizon)


  # Creation of categorical information
  # Time series used without trend to use mean as feature
  tvpDetrend <- tvp$values-stlTraintrend
  tsDetrend <- ts(tvpDetrend,frequency=tvp$frequency)

  # Get clusters
  clusters <- calcClustersForPeriods(timeseries = tsDetrend, frequency = tvp$frequency, doAnomDet = doAnomDet, replace.zeros = replace.zeros, debug = debug)

  if(debug){
    xend <- (length(stlTraintrend)+length(fcTrend))/tvp$frequency+1
    print(paste("frequency:",tvp$frequency))
    plot(stlTrain)
    par(mfrow=c(1,1))
    plot(ts(fcSeason,frequency=tvp$frequency))
    par(mfrow=c(2,1))
    plot(stlTraintrend,xlim=c(0,xend),ylim=c(min(c(stlTraintrend,fcTrend)),max(c(fcTrend,stlTraintrend))))
    lines(fcTrend,col="red")
    plot(stl(ts(tvp$values,frequency=tvp$frequency),s.window = "periodic",t.window=length(tvp$values)/2)$time.series[,2],xlim=c(0,xend))
    print(clusters[,ncol(clusters)])
  }

  # Forecast cluster labels
  clusterLabels <- forecastClusters(clusters = clusters[,ncol(clusters)], frequency = tvp$frequency, timeseries = tsTrain, reps = repsANN, horizon = horizon, debug = debug)



  # Build the covariates matrix
  if(use.log) {
    if(tvp$use.second.freq) {
      xgbcov <- rbind(as.vector(stlTrain$time.series[,1]) + as.vector(stlRemainderSeason),clusterLabels[1:hist.length])
    } else {
      xgbcov <- rbind(stlTrain$time.series[,1],clusterLabels[1:hist.length])
    }
  } else {
    if(tvp$use.second.freq) {
      xgbcov <- rbind(stlTraintrend,as.vector(stlTrain$time.series[,1]) + as.vector(stlRemainderSeason),clusterLabels[1:hist.length])
    } else {
      xgbcov <- rbind(stlTraintrend,stlTrain$time.series[,1],clusterLabels[1:hist.length])
    }
  }
  xgbcov <- as.matrix(t(xgbcov))

  if(!missing(hist.covar)) {
    xgbcov <- cbind(xgbcov, hist.covar)
  }
    
  # Building the training labels
  if(use.log) {
    xgblabel <- tvp$values-stlTraintrend
  } else {
    xgblabel <- tvp$values
  }

  # Learning the XGBoost model
  # xglinear for time series with trend patten in the forecast, gbtree for only seasonal pattern in forecast
  if(estimateBooster(stlTrain)) {
    booster <- "gblinear"
  } else {
    booster <- "gbtree"
  }
  print(booster)
  # Train XGBoost
  fXGB <- doXGB.train(myts = xgblabel, cov = xgbcov, booster = booster, verbose = 0)

  # Build the covariates matrix for the future
  if(use.log) {
    testcov <- rbind(fcSeason[(hist.length+1):total.length],clusterLabels[(hist.length+1):total.length])
  } else {
    testcov <- rbind(fcTrend,fcSeason[(hist.length+1):total.length],clusterLabels[(hist.length+1):total.length])
  }
  testcov <- as.matrix(t(testcov))
  
  if(!missing(future.covar)) {
     testcov <- cbind(testcov, future.covar)
  }
     
  # Unify names
  fXGB$feature_names <- colnames(testcov)
  colnames(xgbcov) <- colnames(testcov)

  # Perform forecast using the covariates
  predXGB <- predict(fXGB,testcov)
  if(use.log) {
    predXGB <- exp(predXGB)*exp(fcTrend)
    tvp$values <- exp(tvp$values)
  }

  # Undo adjustment to positive values
  if(minValue<=0) {
    predXGB <- predXGB - abs(minValue) - 1
  }

  if(save_fc) {
    save.csv(values = predXGB, name = csv.name, path = csv.path)
  }

  endTime <- Sys.time()
  print(paste("Time elapsed for the whole forecast:",difftime(endTime,startTime,units = "secs")))

  par(mfrow=c(2,1))
  
  # Get model of the history
  xgb.model <- predict(fXGB,xgbcov)
  if(use.log) {
    xgb.model <- exp(xgb.model)*exp(stlTraintrend)
  }
  # Undo adjustment to positive values
  if(minValue<=0) {
    xgb.model <- xgb.model - abs(minValue) - 1
    tvp$values <- tvp$values - abs(minValue) - 1
  }
  
  # Calculates the accuracies of the trained model
  accuracyXGB <- accuracy(xgb.model,tvp$values)
  # inner MASE value (fitting of the model)
  tvpTrain <- tvp$values
  MASE <- computeMASE(xgb.model[-1], train = tvpTrain[1], test = tvpTrain[-1], !plot)
  MASE_Multistep <- computeMASEsameValue(xgb.model[-1], train = tvpTrain[1], test = tvpTrain[-1], !plot)
  inner.accuracy <- cbind(accuracyXGB, MASE, MASE_Multistep)
  print(inner.accuracy)
  
  # Build the time series with history and forecast
  fcOnly <- ts(predXGB,frequency=tvp$frequency)
  fcAll <- c(tvp$values,predXGB)
  fcAll <- ts(fcAll,frequency=tvp$frequency)
  
  # Plot the model and the time series
  y.min <- min(min(tvpTrain[-1]),min(xgb.model[-1]))
  y.max <- max(max(tvpTrain[-1]),max(xgb.model[-1]))
  plot(1:length(tvpTrain[-1]), tvpTrain[-1],type="l",col="black", main = 'History (black) and Model (red)', xlab = 'Index', ylab = 'Observation', xlim = c(0, total.length), ylim = c(y.min, y.max))
  lines(1:length(xgb.model[-1]), xgb.model[-1], type = "l", col="red")
  
  # Plot the forecasted time series and the original time series
  y.min <- min(min(fcAll),min(tvp$values))
  y.max <- max(max(fcAll),max(tvp$values))
  plot(1:total.length, as.vector(fcAll),type = 'l',col="red",xlab = 'Index', ylab = 'Observation', main = 'History (black) and Forecast (red)', xlim = c(0, total.length), ylim = c(y.min, y.max))
  lines(1:length(tvp$values), tvp$values)

  # Collect information for output
  output.mean <- fcOnly
  output.x <- tvp$values
  output.residuals <- output.x - ts(xgb.model,frequency=tvp$frequency)
  output.method <- "Telescope"
  output.accuracy <- inner.accuracy
  output.fitted <- xgb.model


  if(use.indicators) {
    output.risky.trend.model <- model$risky_trend_model
    output.high.stl.remainder <- high_remainder
    output <- list(mean=output.mean, x=output.x, residuals=output.residuals, method=output.method,
                   fitted=output.fitted, riskytrend=output.risky.trend.model, highresiduals=output.high.stl.remainder)
  } else {
    output <- list(mean=output.mean, x=output.x, residuals=output.residuals, method=output.method,
                   fitted=output.fitted)
  }

  return(structure(output, class = 'forecast'))

}

