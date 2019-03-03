package com.example.beandiscovery.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.example.beandiscovery.model.Student;

@Component("studentDao")
public class StudentDao {
	private JdbcTemplate jdbc;
	
	@Autowired
	public void setDataSource(DataSource jdbc) {
        this.jdbc = new JdbcTemplate(jdbc);
	
	}

	public List<Student> getStudents()
	{
	   return jdbc.query("select *from student", new RowMapper() {

		@Override
		public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
			Student student = new Student();
			student.setId(rs.getInt("id"));
			student.setName(rs.getString("name"));
			student.setEmail(rs.getString("email"));
			student.setAge(rs.getInt("age"));
			student.setText(rs.getString("text"));
			
			
			return student;
		}
	});
	}

}
