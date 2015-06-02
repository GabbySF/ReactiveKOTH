package workbooks

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Map
import play.api.libs.json.Json




object ListExamples {
  println("kaka ")       //> Welcome to the Scala worksheet
  
  val list:ArrayBuffer[(Int,Int,Int)] =  ArrayBuffer()
                                                  //> list  : scala.collection.mutable.ArrayBuffer[(Int, Int, Int)] = ArrayBuffer(
                                                  //| )
  list += ((1,23,10))                             //> res0: workbooks.ListExamples.list.type = ArrayBuffer((1,23,10))
  list += ((2,23,10))                             //> res1: workbooks.ListExamples.list.type = ArrayBuffer((1,23,10), (2,23,10))
  list                                            //> res2: scala.collection.mutable.ArrayBuffer[(Int, Int, Int)] = ArrayBuffer((1
                                                  //| ,23,10), (2,23,10))
  
  
	val t=(2,23,10)                           //> t  : (Int, Int, Int) = (2,23,10)
  val t2 = t copy(_3 = t._3+10)                   //> t2  : (Int, Int, Int) = (2,23,20)
  
  list foreach {
  	println(_)                                //> (1,23,10)
                                                  //| (2,23,10)
  }
  
  for(i <- 0 until list.size){
  	if(list(i)._1 == 2){
  		list(i) = list(i) copy(_3 = list(i)._3+10, _2 = 3)
  	}
  }
 
 	list                                      //> res3: scala.collection.mutable.ArrayBuffer[(Int, Int, Int)] = ArrayBuffer((1
                                                  //| ,23,10), (2,3,20))
 	
 	
 	val map:Map[Int,Int] = Map()              //> map  : scala.collection.mutable.Map[Int,Int] = Map()
 	
 	map += 1 -> 2                             //> res4: workbooks.ListExamples.map.type = Map(1 -> 2)
 	map += 2 -> 2                             //> res5: workbooks.ListExamples.map.type = Map(2 -> 2, 1 -> 2)
 	map += 3 -> 2                             //> res6: workbooks.ListExamples.map.type = Map(2 -> 2, 1 -> 2, 3 -> 2)
 	if(map.exists(_._1 == 3)){
 		map(3) = map(3)+2
 	}else{
 		map(3) = 2
 	}
 	
 	map                                       //> res7: scala.collection.mutable.Map[Int,Int] = Map(2 -> 2, 1 -> 2, 3 -> 4)
 	
 	
 	
 	val exisitng: Option[Int] = None          //> exisitng  : Option[Int] = None
 	val map1:Map[Int,Option[Int]] = Map(1->exisitng)
                                                  //> map1  : scala.collection.mutable.Map[Int,Option[Int]] = Map(1 -> None)
 	println(Json.toJson(exisitng))            //> null
 	
 	
}