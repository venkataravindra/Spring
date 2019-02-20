package com.example.annotations.bean;

import org.springframework.stereotype.Component;

public class Parent implements GrandParent {

	@Override
	public void profession(String text) {
		System.out.println(text);
		
	}

}
