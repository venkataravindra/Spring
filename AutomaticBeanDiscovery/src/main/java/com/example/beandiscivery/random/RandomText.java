package com.example.beandiscivery.random;

import org.springframework.stereotype.Component;

@Component
public class RandomText {
	private static String[] texts= {
			"i am a student",
			"i am going to college",
			"i want mobile and bike "};
	
	public String getText() {
		return texts[texts.length];
	}

}
