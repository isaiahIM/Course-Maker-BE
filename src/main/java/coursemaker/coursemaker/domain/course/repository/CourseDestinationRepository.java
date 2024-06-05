package coursemaker.coursemaker.domain.course.repository;

import coursemaker.coursemaker.domain.course.entity.CourseDestination;
import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseDestinationRepository extends JpaRepository<CourseDestination, Long> {
    List<CourseDestination> findAllByTravelCourse(TravelCourse travelCourse);

    void deleteAllByTravelCourseId(long travelCourseId);
}