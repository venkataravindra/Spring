package com.example.beandiscovery.AutomaticBeanDiscovery;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.example.beandiscovery.model.School;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("com/example/beandiscovery/beans/beans.xml");
         
        School school = (School) context.getBean("school");
        school.teacherSpeak("This is teacher");
        school.studentSpeak();
        ((ClassPathXmlApplicationContext)context).close();
        
        
    }
}
