package com.mikitahradovich.spikeroog.common

import com.github.tototoshi.csv.CSVReader
import org.apache.logging.log4j.scala.Logging

class FileReader extends Logging {
  def readCsvFile(path: String): Stream[List[String]] = {
    logger.debug(s"Reading file from ${getClass.getResourceAsStream(path).toString}")
    val source = io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    CSVReader.open(source).toStream
  }
}
