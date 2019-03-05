package com.example.beandiscovery.AutomaticBeanDiscovery;

import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import com.example.beandiscovery.dao.StudentDao;

import com.example.beandiscovery.model.Student;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
       ApplicationContext context = new ClassPathXmlApplicationContext("com/example/beandiscovery/beans/beans.xml");
         
        StudentDao studentDao = (StudentDao) context.getBean("studentDao");
        List<Student> students= studentDao.getStudents();
        try {
			for(Student student:students)
			{
				System.out.println(student);
			}
			
        }
        catch(CannotGetJdbcConnectionException e)
        {
        	System.out.println("cannot get database connection");
        }
        catch (DataAccessException e) {
		     System.out.println(e.getMessage());
		}
        ((ClassPathXmlApplicationContext)context).close();
        
        
    }
}
