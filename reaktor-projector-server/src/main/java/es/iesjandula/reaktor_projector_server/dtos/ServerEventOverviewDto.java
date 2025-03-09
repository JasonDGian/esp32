package es.iesjandula.reaktor_projector_server.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerEventOverviewDto
{
	private Long errorEvents;
	private Long canceledEvents;
	private Long pendingEvents;
	private Long deliveredEvents;
	private Long completedEvents;
}
