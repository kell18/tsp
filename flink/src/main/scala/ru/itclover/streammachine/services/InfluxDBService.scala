package ru.itclover.streammachine.services

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import org.influxdb.InfluxDBFactory
import scala.util.Try
import ru.itclover.streammachine.http.utils.ImplicitUtils.StringOps


object InfluxDBService {
  def connectDb(url: String, dbName: String, userName: Option[String] = None, password: Option[String] = None,
                readTimeoutSec: Long = 200L) = {
    val extraConf = new OkHttpClient.Builder().readTimeout(readTimeoutSec, TimeUnit.SECONDS)
    for {
      connection <- Try(InfluxDBFactory.connect(url, userName.orNull, password.orNull, extraConf))
      db <- Try(connection.setDatabase(dbName))
    } yield (connection, db)
  }

  def makeLimit1Query(query: String) = {
    query.replaceLast("""LIMIT \d+""", "", Pattern.CASE_INSENSITIVE) + "LIMIT 1"
  }
}