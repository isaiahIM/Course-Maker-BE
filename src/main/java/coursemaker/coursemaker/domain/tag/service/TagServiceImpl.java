package coursemaker.coursemaker.domain.tag.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import coursemaker.coursemaker.domain.course.entity.TravelCourse;
import coursemaker.coursemaker.domain.course.service.CourseService;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.entity.QDestination;
import coursemaker.coursemaker.domain.destination.service.DestinationService;
import coursemaker.coursemaker.domain.tag.entity.CourseTag;
import coursemaker.coursemaker.domain.tag.entity.DestinationTag;
import coursemaker.coursemaker.domain.tag.entity.QDestinationTag;
import coursemaker.coursemaker.domain.tag.entity.Tag;
import coursemaker.coursemaker.domain.tag.repository.CourseTagRepository;
import coursemaker.coursemaker.domain.tag.repository.DestinationTagRepository;
import coursemaker.coursemaker.domain.tag.repository.TagRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.querydsl.jpa.impl.JPAQueryFactory;


import static coursemaker.coursemaker.domain.course.entity.QTravelCourse.travelCourse;
import static coursemaker.coursemaker.domain.destination.entity.QDestination.destination;
import static coursemaker.coursemaker.domain.tag.entity.QCourseTag.courseTag;
import static coursemaker.coursemaker.domain.tag.entity.QDestinationTag.destinationTag;

/*
 *TODO:
 * 메소드 실패시 커스텀 예외처리 하기
 * */
@Service
@Transactional
@RequiredArgsConstructor
public class TagServiceImpl implements TagService{

    private final CourseTagRepository courseTagRepository;
    private final TagRepository tagRepository;
    private final DestinationTagRepository destinationTagRepository;

    private final CourseService courseService;
    private final DestinationService destinationService;

    private final JPAQueryFactory queryFactory;

    /*****태그 기본 CRUD*****/
    @Override
    public Tag createTag(Tag tag){

        /*태그의 이름은 고유해야 한다.*/
        if(tagRepository.findByname(tag.getName()).isPresent()){
            throw new RuntimeException("이미 태그가 존재합니다");
        }

        return tagRepository.save(tag);
    }

    @Override
    public Tag findById(Long id){
        return tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("태그가 없습니다"));
    }

    @Override
    public Tag findByTagName(String name){
        return tagRepository.findByname(name)
                .orElseThrow(() -> new RuntimeException("태그가 없습니다"));
    }

    @Override
    public List<Tag> findAllTags(){
        return tagRepository.findAll();
    }

    // ISSUE: 제대로 업데이트 됬는지 확인하고 싶은데 어떻게 해야 깔끔하게 할 수 있을까요? 아니면 굳이 검증을 해서 반환을 할 필요가 없을까요?
    @Override
    public Tag updateTag(Tag tag){

        /*변경할 타겟 태그를 못찾음.*/
        if(tagRepository.findById(tag.getId()).isEmpty()){
            throw new RuntimeException("변경할 태그가 없습니다.");
        }

        /*태그의 이름 중복 확인*/
        if(tagRepository.findByname(tag.getName()).isPresent()){
            if(!tagRepository.findByname(tag.getName()).get()
                    .getId()
                    .equals(tag.getId())
            ){
                throw new RuntimeException("이미 태그가 존재합니다(중복)");
            }
        }

        return tagRepository.save(tag);
    }

    // ISSUE: 이것도 제대로 삭제됬는지 검증하는 절차가 필요할까요?
    @Override
    public void deleteById(Long id){
        /*태그와 연결된 여행지, 코스 연관관계 삭제*/
        courseTagRepository.deleteAllByTagId(id);
        destinationTagRepository.deleteAllByTagId(id);

        /*태그 삭제*/
        tagRepository.deleteById(id);
    }

    /******태그-코스 ******/


    @Override
    public void addTagsByCourse(Long courseId, List<Long> tagIds){

        if(tagIds.isEmpty()){
            throw new RuntimeException("태그가 없습니다.");
        }

        // 중복된 태그를 제외하고 추가함
        for (Long tagId : tagIds) {
            CourseTag courseTag = new CourseTag();
            Tag tag = tagRepository.findById(tagId).orElseThrow(() -> new RuntimeException("태그가 없습니다."));

            if(courseTagRepository.findByCourseIdAndTagId(courseId, tagId).isEmpty()){
                courseTag.setTag(tag);
                courseTag.setCourse(courseService.findById(courseId));
                courseTagRepository.save(courseTag);
            }
        }
    }


    @Override
    public List<Tag> findAllByCourseId(Long courseId){
        List<CourseTag> courseTags = courseTagRepository.findAllByCourseId(courseId);

        // CourseTag에서 태그 추출
        List<Tag> tags = courseTags
                .stream()
                .map(CourseTag::getTag)
                .collect(Collectors.toList());

        return tags;
    }

    @Override
    public List<TravelCourse> findAllCourseByTagIds(List<Long> tagIds, Pageable pageable, OrderBy orderBy){

        BooleanBuilder condition = new BooleanBuilder();
        OrderSpecifier<?> orderBySpecifier = null;

        // TODO: 인기순 정렬 로직 설정, 평균별점 로직 설정(고도화)
        switch(orderBy) {
            case VIEWS:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
            case NEWEST:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.createdAt);
                break;
            case POPULAR:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
            case RATING:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
        }

        /*다중검색*/
        for(Long id : tagIds) {
            condition.or(courseTag.tag.id.eq(id));
        }

        List<TravelCourse> courses = queryFactory
                .select(courseTag, courseTag.course.count())
                .from(courseTag)// 코스태그에서 선택(코스에는 FK가 없음)
                .leftJoin(courseTag.course, travelCourse)// 코스-코스태그 조인
                .where(condition)// 다중태그
                .groupBy(courseTag.course)// 코스로 묶어서
                .having(courseTag.course.count().gt(tagIds.size()-1))// 중복된 부분만 추출함
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .offset(pageable.getOffset())// 페이지네이션
                .limit(pageable.getPageSize())
                .fetch()
                .stream()
                .map(n -> n.get(courseTag).getCourse())
                .collect(Collectors.toList());

        return courses;
    }



    @Override
    public void deleteTagByCourse(Long courseId, List<Tag> tags){

        if(tags.isEmpty()){
            throw new RuntimeException("태그가 없습니다.");
        }

        // 코스에 태그들 삭제
        for (Tag tag : tags) {
            if(courseTagRepository.findByCourseIdAndTagId(courseId, tag.getId()).isPresent()){
                courseTagRepository.deleteByCourseIdAndTagId(courseId, tag.getId());
            }
        }
    }

    @Override
    public void deleteAllTagByCourse(Long courseId){
        courseTagRepository.deleteAllByCourseId(courseId);
    }



    /******태그-여행지 ******/

    @Override
    public void addTagsByDestination(Long destinationId, List<Long> tagIds){

        if(tagIds.isEmpty()){
            throw new RuntimeException("태그가 없습니다.");
        }

        // 중복된 태그를 제외하고 추가함
        for (Long tagId : tagIds) {
            DestinationTag destinationTag = new DestinationTag();
            Tag tag = tagRepository.findById(tagId).orElseThrow(() -> new RuntimeException("태그가 없습니다."));

            if(destinationTagRepository.findByDestinationIdAndTagId(destinationId, tagId).isEmpty()){
                destinationTag.setTag(tag);
                destinationTag.setDestination(destinationService.findById(destinationId)); //오류나서 .get() 삭제
                destinationTagRepository.save(destinationTag);
            }
        }
    }

    @Override
    public List<Tag> findAllByDestinationId(Long destinationId){
        List<DestinationTag> destinationTags = destinationTagRepository.findAllByDestinationId(destinationId);
        List<Tag> tags = new ArrayList<>();

        // CourseTag에서 태그 추출
        tags = destinationTags
                .stream()
                .map(DestinationTag::getTag)
                .collect(Collectors.toList());

        return tags;
    }


    @Override
    public List<Destination> findAllDestinationByTagIds(List<Long> tagIds, Pageable pageable, OrderBy orderBy) {
        BooleanBuilder condition = new BooleanBuilder();
        OrderSpecifier<?> orderBySpecifier = null;

        // TODO: 인기순 정렬 로직 설정, 평균별점 로직 설정(고도화)
        switch(orderBy) {
            case VIEWS:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
            case NEWEST:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.createdAt);
                break;
            case POPULAR:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
            case RATING:
                orderBySpecifier = new OrderSpecifier<>(Order.DESC, destination.views);
                break;
        }

        /*다중검색*/
        for(Long id : tagIds) {
            condition.or(destinationTag.tag.id.eq(id));
        }


        List<Destination> destinations = queryFactory
                .select(destinationTag, destinationTag.destination.count())
                .from(destinationTag)// 여행지 태그에서 선택(여행지에는 FK가 없음)
                .leftJoin(destinationTag.destination, destination)// 코스-코스태그 조인
                .where(condition)// 다중태그 조건 검색
                .groupBy(destinationTag.destination)// 여행지로 묶어서
                .having(destinationTag.destination.count().gt(tagIds.size()-1))// 중복된 부분만 추출
                .orderBy(orderBySpecifier)// 정렬 조건 설정
                .offset(pageable.getOffset())// 페이지네이션
                .limit(pageable.getPageSize())
                .fetch()// 쿼리 실행
                .stream()// 리스트 -> 스트림으로 처리
                .map(n -> n.get(destinationTag).getDestination())// 여행지태그 -> 여행지 변환
                .collect(Collectors.toList());

        return destinations;
    }


    @Override
    public void deleteTagByDestination(Long destinationId, List<Tag> tags){

        if(tags.isEmpty()){
            throw new RuntimeException("태그가 없습니다.");
        }

        // 여행지에 포함된 태그들 삭제
        for (Tag tag : tags) {
            if(destinationTagRepository.findByDestinationIdAndTagId(destinationId, tag.getId()).isPresent()){
                destinationTagRepository.deleteByDestinationIdAndTagId(destinationId, tag.getId());
            }
        }
    }

    @Override
    public void deleteAllTagByDestination(Long destinationId){
        destinationTagRepository.deleteAllByDestinationId(destinationId);
    }

}
