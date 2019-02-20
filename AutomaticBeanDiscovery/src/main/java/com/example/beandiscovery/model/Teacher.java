package com.example.beandiscovery.model;

import org.springframework.stereotype.Component;

@Component
public class Teacher implements Period{

	@Override
	public void speak(String text) {
		System.out.println("text from teacher");
		
	}
	

}
