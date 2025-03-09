package es.iesjandula.reaktor_projector_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelOverviewDto
{
	private String modelname;
	private long associatedProjectors;
	private long associatedCommands;
}
