package ua.zapys.domain

/** One immutable save request for primary, draft and back-save actions. */
data class AppointmentSaveRequest(val appointment: Appointment,val newClient: Client?)
class AppointmentSaveSession(val draft: AppointmentDraft,clients:List<Client>,inlineId:String,var inlineName:String,var inlinePhone:String) {
 var inlineId=inlineId
  private set
 val clients=clients.toMutableList()
 fun prepare(requestedStatus:AppointmentStatus):AppointmentSaveRequest {
  val client=if(inlineName.isNotBlank() || inlinePhone.isNotBlank()) Client(id=inlineId,firstName=inlineName.trim(),phone=inlinePhone.trim()).also { Rules.validateClient(it) } else null
  return AppointmentSaveRequest(draft.build(clients+listOfNotNull(client),requestedStatus,client?.id ?: draft.clientId),client)
 }
 /** Called only after the client transaction succeeds, even if appointment save later fails. */
 fun clientPersisted(client:Client) { if(clients.none { it.id==client.id }) clients.add(client); draft.clientId=client.id; inlineName=""; inlinePhone=""; inlineId=newId() }
}
