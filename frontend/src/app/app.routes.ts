import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/validate/validate-page').then((m) => m.ValidatePage) },
  { path: 'result', loadComponent: () => import('./features/result/result-page').then((m) => m.ResultPage) },
  { path: '**', redirectTo: '' },
];
