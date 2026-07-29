select id, owner_id, name, description, created_at, updated_at
from projects
where
/*%if admin */
  true
/*%else*/
  owner_id = /* userId */0
/*%end*/
order by updated_at desc, id desc
