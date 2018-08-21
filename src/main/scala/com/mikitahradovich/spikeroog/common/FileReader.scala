package com.mikitahradovich.spikeroog.common

import java.io.File

import com.github.tototoshi.csv.CSVReader

class FileReader {
  def readCsvFile(filename: String): Stream[List[String]] = {
    val file = new File(getClass.getClassLoader.getResource(filename).getFile)
    CSVReader.open(file).toStream
  }
}
