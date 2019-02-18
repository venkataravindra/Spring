package com.example.spring.bean;

public class PersonFactory {
	public  Person createPerson(int id, String name)
	{
		System.out.println(" Person using factory ");
	return new Person(id,name);	
	}
}
