//possible improvements
//1- apply chromosome splitting at this level, maybe create and endpos now and fill it if range
//2- pipeline everything vertically
// 3 adjust system to improve efficiency
//4- implement a key-value approach-> where key is the combination of chrom+pos+ref+alt and then use reducedbyKey for grouping ,it should be much faster
// 5 key-value approach should be used also for "upserting" elasticsearch
// reduce file size numbers by coalensce command
package steps


import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SaveMode
object gzToParquet {
case class rawTable(pos:Int,
                    ID : String,
                    ref :String ,
                    alt : String,
                    qual:String,
                    filter:String,
                    info : String,
                    format:String,
                    Sample : String,
                     SampleID: String)
    
def chromStrToInt(chrom:String)={
  chrom match {
    case "MT" =>"23"
    case "X" => "24"
    case "Y" => "25"
    case _ => chrom
  }
}


//val files = List("E000001")
//val chromList = List("X")

def file_to_parquet(sc :org.apache.spark.SparkContext, origin_path: String, destination : String, chrom:String,name:String)=
{      //remove header
  


// this is used to implicitly convert an RDD to a DataFrame.
       val file = sc.textFile(origin_path).filter(line => !line.startsWith("#"))
       //they have to be processed by chrom all together in order to have num partitions higher than 1
       val raw_file = file.map(_.split("\t"))
         .map(p => rawTable(p(1).trim.toInt, p(2), p(3), p(4), p(5), p(6), p(7), p(8),p(9), name.split("/")(name.split("/").length-1)))
       raw_file
}

def main(sc:org.apache.spark.SparkContext,
		path : String, 
		chromList : List[String],
    files:List[(String,String)],
		destination : String,
		numPartitions:Int=4)= {

  val sqlContext = new org.apache.spark.sql.SQLContext(sc)
  import sqlContext.implicits._

  for (chrom <- chromList) yield {
      var RDD: org.apache.spark.rdd.RDD[steps.gzToParquet.rawTable] = null;
      for ((file, index) <- files.zipWithIndex) yield {
        if (chrom=="Y" && file._2=="F")
          println("Do nothing, because female DNA does not bring Y chrom" )
        else if (index == 0) {
          RDD = file_to_parquet(sc, getName(path=path, file=file,chrom=chrom), destination, chrom, file._1)
          if (files.length ==1)
            RDD.toDF.write.mode(SaveMode.Overwrite).save(destination+"/chrom="+chromStrToInt(chrom))

        }
        else if (index == files.length - 1) {
          RDD = file_to_parquet(sc, getName(path=path, file=file,chrom=chrom), destination, chrom, file._1).union(RDD)
          RDD.toDF.write.mode(SaveMode.Overwrite).save(destination+"/chrom="+chromStrToInt(chrom))
        }

        else
          {RDD = file_to_parquet(sc, getName(path=path, file=file,chrom=chrom), destination, chrom, file._1).union(RDD)}
      }
      RDD
    }
}

  def getName(path:String, chrom:String, file:(String,String)):String={
    if ( chrom=="X" ){
      if (file._2=="M")
        path + file._1 +"." + chrom + ".haplodiploid.annot.g.vcf.gz"
      else
        path + file._1 +"." + chrom + ".annot.snpEff.p.g.vcf.gz"

    }
    else if (chrom=="Y" && file._2=="M")
        path + file._1 +"." + chrom + ".haploid.annot.g.vcf.gz"

    else path +file._1 +"." + chrom + ".annot.snpEff.p.g.vcf.gz"

  }
       
/*file_to_parquet("/user/dpiscia/gvcf10bands/E000010.g.vcf.gz","/user/dpiscia/test/trio","E000010")
file_to_parquet("/user/dpiscia/gvcf10bands/E000036.g.vcf.gz","/user/dpiscia/test/trio","E000036")
file_to_parquet("/user/dpiscia/gvcf10bands/E000037.g.vcf.gz","/user/dpiscia/test/trio","E000037")*/

}