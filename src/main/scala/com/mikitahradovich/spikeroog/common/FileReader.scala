package com.mikitahradovich.spikeroog.common

import com.github.tototoshi.csv.CSVReader
import org.apache.logging.log4j.scala.Logging

class FileReader extends Logging {
  def readCsvFile(filename: String): Stream[List[String]] = {
    logger.debug(s"Reading resource $filename")
    val source = io.Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(filename), "UTF-8")
    CSVReader.open(source).toStream
  }
}
