package org.example.spring.HelloWorld;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.example.spring.bean.Address;
import com.example.spring.bean.FruitBasket;
import com.example.spring.bean.Person;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
       //ApplicationContext context =new FileSystemXmlApplicationContext("beans.xml");
       ApplicationContext context =new ClassPathXmlApplicationContext("com/example/spring/beans/beans.xml");
       
      /* Person person = (Person) context.getBean("person");
        person.speak();
       Address address = (Address) context.getBean("address");
       Address address2 = (Address) context.getBean("address2");

       System.out.println(person);
        System.out.println(address2);*/
       FruitBasket basket = (FruitBasket) context.getBean("fruitbasket");
       System.out.println(basket);
        ((ClassPathXmlApplicationContext)context).close();
       
    }
}
