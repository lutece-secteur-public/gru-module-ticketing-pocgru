INSERT INTO workflow_workflow VALUES (201,'Workflow spécifique au candidat du PoC GRU','Workflow spécifique au candidat du PoC GRU','2016-01-13 08:36:34',1,'all');

INSERT INTO workflow_state VALUES (201,'Nouveau','Nouveau',201,1,0,NULL,1);
INSERT INTO workflow_state VALUES (202,'Envoyé au EndPoint','Envoyé au EndPoint',201,0,0,NULL,2);

INSERT INTO workflow_action VALUES (201,'Envoyer au EndPoint','Envoyer au EndPoint',201,201,202,1,1,0,1,0);

INSERT INTO workflow_task VALUES (201,'taskTicketingSendRestRequest',201,3);