package com.example.beandiscovery.model;


import javax.inject.Inject;

import org.springframework.stereotype.Component;

@Component
public class School {
	private Teacher teacher;
	private Student student;
	
	@Inject
	public void setTeacher(Teacher teacher) {
		this.teacher = teacher;
	}
	@Inject
	public void setStudent(Student student) {
		this.student = student;
	}
	public void teacherSpeak(String text) {
		teacher.speak(text);
	}
	
    public void studentSpeak( ) {
    	if(student != null) {
    		student.speak();
    	}
    }
    

}


