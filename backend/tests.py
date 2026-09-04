#!/usr/bin/env python3
import os,tempfile,unittest,importlib.util
os.environ['APPBLOCKER_DATA']=tempfile.mkdtemp()
spec=importlib.util.spec_from_file_location('server',os.path.join(os.path.dirname(__file__),'server.py'));s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
class T(unittest.TestCase):
 def setUp(self):s.init_db()
 def test_password(self):
  s.set_password('password123');
  with s.db() as c:r=c.execute('select * from admin').fetchone()
  self.assertEqual(s.hash_password('password123',r['pass_salt']),r['pass_hash'])
 def test_session(self):self.assertTrue(s.valid_session(s.make_session()))
 def test_system_protection(self):self.assertTrue(s.protected_package('com.android.settings'));self.assertFalse(s.protected_package('com.google.android.youtube'))

if __name__=='__main__':unittest.main()
