-- Estado de la transmisión de cada subasta: solo se guarda si hay video activo y quién lo emite.
create table transmision (
    subasta_id    uuid                     primary key,
    subastador_id uuid                     not null,
    activa        boolean                  not null,
    iniciada_en   timestamp with time zone,
    detenida_en   timestamp with time zone
);
