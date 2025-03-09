package es.iesjandula.reaktor_projector_server.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.iesjandula.reaktor_projector_server.dtos.ProjectorDto;
import es.iesjandula.reaktor_projector_server.dtos.ProjectorInfoDto;
import es.iesjandula.reaktor_projector_server.entities.Classroom;
import es.iesjandula.reaktor_projector_server.entities.Projector;
import es.iesjandula.reaktor_projector_server.entities.ProjectorModel;
import es.iesjandula.reaktor_projector_server.entities.ids.ProjectorId;

public interface IProjectorRepository extends JpaRepository<Projector, ProjectorId>
{
	
	@Query("""
		    SELECT new es.iesjandula.reaktor_projector_server.dtos.ProjectorInfoDto( 
		        pro.model.modelName, 
		        pro.classroom.classroomName, 
		        pro.classroom.floor.floorName
		    ) 
		    FROM Projector pro 
		    WHERE ( :classroom IS NULL OR pro.classroom.classroomName = :classroom ) 
		    AND ( :floor IS NULL OR pro.classroom.floor.floorName = :floor ) 
		    AND ( :model IS NULL OR pro.model.modelName = :model ) 
		    ORDER BY pro.model.modelName, pro.classroom.floor.floorName, pro.classroom.classroomName 
		""")
		public Page<ProjectorInfoDto> getProjectorOrderByModelName(Pageable pageable, String classroom, String floor, String model);

	@Query("""
		    SELECT new es.iesjandula.reaktor_projector_server.dtos.ProjectorInfoDto( 
		        pro.model.modelName, 
		        pro.classroom.classroomName, 
		        pro.classroom.floor.floorName
		    ) 
		    FROM Projector pro 
		    WHERE ( :classroom IS NULL OR pro.classroom.classroomName = :classroom ) 
		    AND ( :floor IS NULL OR pro.classroom.floor.floorName = :floor ) 
		    AND ( :model IS NULL OR pro.model.modelName = :model )
		    ORDER BY pro.classroom.floor.floorName, pro.classroom.classroomName 
		""")
		public Page<ProjectorInfoDto> getProjectorsOrderByFloorAndClassroom(Pageable pageable, String classroom, String floor, String model);

	@Query(
			"""
			SELECT new es.iesjandula.reaktor_projector_server.dtos.ProjectorDto( pro.model.modelName, pro.classroom.classroomName) 
			FROM Projector pro 
			WHERE LOWER(pro.classroom) = LOWER(:classroom)
			""")
	public List<ProjectorDto> getProjectorByClassroom( @Param("classroom") String classroom ); 
	
	@Query(
			"""
			SELECT COUNT(*) 
			FROM Projector pro 
			WHERE LOWER(pro.model.modelName) = LOWER(:modelname) 
			""")
	public long countProjectorAssociatedModel( @Param("modelname") String modelname );
	
	@Query(
			"""
			SELECT COUNT(*) 
			FROM Projector pro 
			WHERE LOWER(pro.classroom.floor.floorName) = LOWER(:floorname) 
			""")
	public long countProjectorOnFloor( @Param("floorname") String floorname );
	
	
}
