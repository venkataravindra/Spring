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
              
        
        try {
        	List<Student> students= studentDao.getStudents();
			for(Student student:students)
			{
				System.out.println(student);
			}
			  
			System.out.println("*************Call by Id*****************************");
			Student student1 = studentDao.getStudent(1);
			System.out.println("getting by id " +student1);
			System.out.println("************* end of Call by Id*****************************");
        	Student student3 = new Student();
        	student3.setId(4);
        	student3.setName("suresh");
        	student3.setEmail("suresh@gmail.com");
        	student3.setAge(26);
        	student3.setText("suresh lives in guntur");
        	 int x = studentDao.createStudent(student3);
        	 if(x>0)
        	 {
        		 System.out.println("student creation successfull");
        	 }
        	 else
        	 {
        		 System.out.println("try again");
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
