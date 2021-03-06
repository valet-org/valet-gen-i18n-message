package org.valet.app

import java.io._

import com.typesafe.config.{Config, ConfigFactory}
import org.valet.common.Loaders.ConfDto
import org.valet.common.{Loaders, ValetUtility}

import scalaj.http.{Http, HttpRequest, HttpResponse}


object Launcher extends ValetUtility {

  def main(args: Array[String]) {
    args.toList match {
      case name :: params if name == "init" => init(params.headOption.getOrElse(new File(".").getCanonicalPath + "/valet.conf"))
      case name :: params if name == "gen"  => gen(params.headOption.getOrElse(new File(".").getCanonicalPath + "/valet.conf"))
      case _                                => showUsage()
    }
  }

  def showUsage(): Unit = {
    println(
      s"""
         | Usage:
         |        sbt "run init $$(pwd)/valet.conf"
         |        sbt "run gen  $$(pwd)/valet.conf"
       """.stripMargin
    )
  }

  def init(filepath: String) {
    val conf: Config = ConfigFactory.parseFile(new File(filepath))
    val confDto: ConfDto = Loaders.getConfDto(conf)
    val k1 = confDto.modulesI18nMessageConfIsUse
    val k2 = confDto.modulesI18nMessageConfI18nList
    val baseLangage = "ja"

    if (k1 == "YES") {
      cli("wget --no-check-certificate  https://github.com/play-valet/valet-gen-i18n-message/archive/master.tar.gz")
      cli("tar xpvf master.tar.gz")
      cli("rm master.tar.gz")
      cli("mv valet-gen-i18n-message-master valet-gen-i18n-message")
      cli("rm -rf valet-gen-i18n-message/build.sbt")
      forceMkdir(new File("./conf"))
      forceMkdir(new File("./valet"))
      forceMkdir(new File("./valet/downloads"))
      cli("rm -rf valet/downloads/valet-gen-i18n-message")
      cli("mv valet-gen-i18n-message valet/downloads/")
      k2.foreach { ln =>
        cli(s"cp ./valet/downloads/valet-gen-i18n-message/default/messages.${ln} ./conf")
      }
    }
  }

  def gen(filepath: String) {
    val conf: Config = ConfigFactory.parseFile(new File(filepath))
    val confDto: ConfDto = Loaders.getConfDto(conf)
    val k1 = confDto.modulesI18nMessageConfIsUse
    val k2 = confDto.modulesI18nMessageConfI18nList
    val baseLangage = "ja"

    if (k1 == "YES") {
      k2.foreach { ln =>
        if (ln != baseLangage) {
          generateI18nMsgFile(ln, baseLangage)
        }
      }
    }

  }

  private def generateI18nMsgFile(toLang: String, baseLangage: String) = {
    val conf: Config = ConfigFactory.parseFile(new File("./azure-key.conf"))
    val apikey = conf.getString("key")
    val token = getToken(apikey).get

    val inBuffer = new BufferedReader(new InputStreamReader(new FileInputStream(s"./conf/messages.${baseLangage}"), "UTF-8"))
    val outBuffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(s"./conf/messages.${toLang}"), "UTF-8"))

    val lines: Seq[String] = (Iterator.continually(inBuffer.readLine()).takeWhile(_ != null).toList)
    val resultList: Seq[String] = lines.map { line =>
      val confKey = line.split("=").headOption.getOrElse("")
      val confVal = line.split("=").last

      val resultConfVal = translate(token, confVal, toLang).getOrElse("")
      val resultConfVal2: String = resultConfVal.replaceAll("""<("[^"]*"|'[^']*'|[^'">])*>""", "")
      confKey + "=" + resultConfVal2
    }
    val buf2 = new StringBuilder
    resultList.foreach { f =>
      buf2.append(f)
      buf2.append("\n")
    }
    outBuffer.write(buf2.toString)
    outBuffer.flush()
  }

  private def getToken(apikey: String): Option[String] = {
    try {
      val request: HttpRequest = Http("https://api.cognitive.microsoft.com/sts/v1.0/issueToken").headers(Seq(
        ("Content-Type", "application/json"),
        ("Accept", "application/jwt"),
        ("Ocp-Apim-Subscription-Key", apikey)
      )).postForm

      val res: HttpResponse[String] = request.asString
      if (res.code == 200) {
        Some(res.body)
      } else {
        None
      }
    } catch {
      case e: Throwable => System.out.println("" + e); None
    }
  }

  private def translate(token: String, src: String, toLang: String): Option[String] = {
    try {
      val key = "Bearer " + token
      val request: HttpRequest = Http("https://api.microsofttranslator.com/v2/http.svc/Translate").params(Seq(
        ("appid", key),
        ("Accept", "application/xml"),
        ("text", src),
        ("to", toLang)
      ))

      val res: HttpResponse[String] = request.asString
      if (res.code == 200) {
        Some(res.body)
      } else {
        None
      }
    } catch {
      case e: Throwable => System.out.println("" + e); None
    }
  }
}
