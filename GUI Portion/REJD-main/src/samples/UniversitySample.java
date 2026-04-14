package samples;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class University extends BaseEntity implements Schedulable, Serializable {

    private String name;
    private Department department;
    private List<Course> courses;
    private Professor dean;
    private Status status;

    public University(String name, Department department, Professor dean) {
        this.name = name;
        this.department = department;
        this.dean = dean;
        this.courses = new ArrayList<>();
        this.status = Status.ACTIVE;
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public Course findCourseByCode(String code) {
        for (Course course : courses) {
            if (course.getCode().equals(code)) {
                return course;
            }
        }
        return null;
    }

    public void assignProfessor(Professor professor, Course course) {
        course.setProfessor(professor);
    }

    @Override
    public void schedule() {
        System.out.println("Scheduling university events...");
    }

    public String getName() {
        return name;
    }

    public Department getDepartment() {
        return department;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public Professor getDean() {
        return dean;
    }

    public Status getStatus() {
        return status;
    }
}

abstract class BaseEntity {
    private long id;

    public long getId() {
        return id;
    }
}

interface Schedulable {
    void schedule();
}

class Department {
    private String title;
    private Professor head;

    public Department(String title, Professor head) {
        this.title = title;
        this.head = head;
    }

    public String getTitle() {
        return title;
    }

    public Professor getHead() {
        return head;
    }
}

class Course {
    private String code;
    private Professor professor;
    private List<Student> students;

    public Course(String code) {
        this.code = code;
        this.students = new ArrayList<>();
    }

    public String getCode() {
        return code;
    }

    public Professor getProfessor() {
        return professor;
    }

    public void setProfessor(Professor professor) {
        this.professor = professor;
    }

    public void enrollStudent(Student student) {
        students.add(student);
    }
}

class Professor {
    private String name;

    public Professor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class Student {
    private String name;
    private Grade grade;

    public Student(String name, Grade grade) {
        this.name = name;
        this.grade = grade;
    }

    public String getName() {
        return name;
    }

    public Grade getGrade() {
        return grade;
    }
}

enum Status {
    ACTIVE, INACTIVE
}

enum Grade {
    A, B, C, D, F
}

class EmptyOffice {
}