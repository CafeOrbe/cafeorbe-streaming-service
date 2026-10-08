-- Un corte breve del emisor (microcorte de red, reconexión de LiveKit) ya no detiene la transmisión al instante:
-- se anota desde cuándo falta su video y solo se detiene si no vuelve dentro del periodo de gracia.
alter table transmision add column emisor_ausente_desde timestamp with time zone;
