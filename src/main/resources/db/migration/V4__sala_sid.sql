-- Cada transmisión recuerda el SID de su sala de LiveKit. Al detener y volver a iniciar, LiveKit crea otra sala con
-- el mismo nombre y otro SID: un webhook room_finished atrasado de la sala anterior ya no detiene la transmisión nueva.
alter table transmision add column sala_sid varchar(64);
