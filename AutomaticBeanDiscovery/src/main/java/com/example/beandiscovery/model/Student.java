package com.example.beandiscovery.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Student {

	
	private int id=0;
	private String text="hello";
	
	
	@Autowired
	public void setId(@Value("312")int id) {
		this.id = id;
	}
    @Autowired
	public void setText(@Value("Hello People")String text) {
		this.text = text;
	}

	public void speak() {
		// TODO Auto-generated method stub
		System.out.println("Student id:"+id+":student says "+text);
		
	}

	 
}
