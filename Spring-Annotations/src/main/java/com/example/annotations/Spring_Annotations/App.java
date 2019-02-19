package com.example.annotations.Spring_Annotations;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.example.annotations.bean.FamilyDetail;

public class App 
{
    public static void main( String[] args )
    {
    	
ApplicationContext context = new ClassPathXmlApplicationContext("com/example/annotations/beans/beans.xml");

		FamilyDetail familydetail = (FamilyDetail) context.getBean("familydetail");
		
		
		familydetail.parentJob("parent job: Physician");
		familydetail.childrenJob("Children job : cardiologist");
		
		((ClassPathXmlApplicationContext)context).close();
	
    }
}
