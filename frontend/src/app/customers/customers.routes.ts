import { Routes } from '@angular/router';
import { CustomerDetail } from './customer-detail';
import { CustomerForm } from './customer-form';
import { CustomerList } from './customer-list';

export const customersRoutes: Routes = [
  { path: '', component: CustomerList },
  { path: 'new', component: CustomerForm },
  { path: ':id/edit', component: CustomerForm },
  { path: ':id', component: CustomerDetail },
];
