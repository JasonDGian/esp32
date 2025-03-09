package es.iesjandula.reaktor_projector_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneralCountOverviewDto
{
	private Long numberOfModels;
	private Long numberOfActions;
	private Long numberOfCommands;
	private Long numberOfProjectors;
	private Long numberOfFloors;
	private Long numberOfClassrooms;
}
